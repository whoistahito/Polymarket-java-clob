package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Signing and submission are independently reachable; {@link #place} is a thin convenience over both. */
public final class Trading {

    /** Official limits (constraints.json): a batch beyond these is never silently chunked. */
    public static final int MAX_ORDERS_PER_BATCH = 15;
    public static final int MAX_ORDER_IDS_PER_CANCEL = 1000;

    private final OrderSigner signer;
    private final OrderSubmitter submitter;
    private final OrderBatch batch;
    private final TradeReader tradeReader;
    private final Clock clock;
    private final Sleeper sleeper;

    /** Injected so a reconciliation poll loop can be tested without a real wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public Trading(OrderSigner signer, OrderSubmitter submitter, OrderBatch batch,
            TradeReader tradeReader, Clock clock) {
        this(signer, submitter, batch, tradeReader, clock, d -> Thread.sleep(d.toMillis()));
    }

    public Trading(OrderSigner signer, OrderSubmitter submitter, OrderBatch batch,
            TradeReader tradeReader, Clock clock, Sleeper sleeper) {
        this.signer = Objects.requireNonNull(signer, "signer");
        this.submitter = Objects.requireNonNull(submitter, "submitter");
        this.batch = Objects.requireNonNull(batch, "batch");
        this.tradeReader = Objects.requireNonNull(tradeReader, "tradeReader");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public SignedOrder sign(AssetId asset, Side side, PusdAmount pusdLeg, ShareQuantity shareLeg,
            MarketRules rules, SigningContext context) {
        return signer.sign(asset, side, pusdLeg, shareLeg, rules, context);
    }

    /** Never replayed: one signed order produces exactly one {@code POST /order}. */
    public SubmissionOutcome submit(SignedOrder order, OrderPlacement placement) {
        return submitter.submit(order, placement);
    }

    public SubmissionOutcome place(AssetId asset, Side side, PusdAmount pusdLeg,
            ShareQuantity shareLeg, MarketRules rules, SigningContext context, OrderPlacement placement) {
        return submit(sign(asset, side, pusdLeg, shareLeg, rules, context), placement);
    }

    /** One {@code POST /orders} for the whole batch; a batch over the official limit sends nothing. */
    public BatchSubmissionOutcome submitBatch(List<BatchItem> items) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("a batch must contain at least one order");
        }
        if (items.size() > MAX_ORDERS_PER_BATCH) {
            throw new IllegalArgumentException("batch of " + items.size()
                    + " orders exceeds the official limit of " + MAX_ORDERS_PER_BATCH);
        }
        ApiCredentials credentials = items.get(0).placement().credentials();
        String signer = items.get(0).order().signer();
        boolean uniform = items.stream().allMatch(i -> i.placement().credentials().equals(credentials)
                && i.order().signer().equals(signer));
        if (!uniform) {
            throw new IllegalArgumentException(
                    "a batch is one signed request: every item must share the same credentials and signer");
        }
        return batch.submitBatch(items);
    }

    /** One {@code DELETE /orders} for the whole set; invalid IDs are rejected before it is sent. */
    public CancellationOutcome cancel(ApiCredentials credentials, String address, List<String> orderIds)
            throws IOException {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(orderIds, "orderIds");
        if (orderIds.isEmpty()) {
            throw new IllegalArgumentException("cancel needs at least one order id");
        }
        if (orderIds.size() > MAX_ORDER_IDS_PER_CANCEL) {
            throw new IllegalArgumentException("cancelling " + orderIds.size()
                    + " order ids exceeds the official limit of " + MAX_ORDER_IDS_PER_CANCEL);
        }
        if (orderIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("order ids must not be blank");
        }
        if (orderIds.size() != Set.copyOf(orderIds).size()) {
            throw new IllegalArgumentException("order ids must not contain duplicates");
        }
        return batch.cancel(credentials, address, orderIds);
    }

    /**
     * Polls the given trade IDs until every one reaches CONFIRMED/FAILED or {@code timeout}
     * elapses. A delayed transaction hash simply shows up on a later poll; a timeout is
     * {@link ReconciliationOutcome.Pending}, never a reported failure.
     */
    public ReconciliationOutcome reconcile(ApiCredentials credentials, String address, String orderId,
            List<String> tradeIds, Duration timeout, Duration pollInterval) throws IOException {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(tradeIds, "tradeIds");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(pollInterval, "pollInterval");
        Instant deadline = clock.instant().plus(timeout);

        while (true) {
            List<SettledTrade> trades = tradeReader.byIds(credentials, address, tradeIds);
            Map<String, SettledTrade> byId = new LinkedHashMap<>();
            // A duplicate record for the same ID keeps only the latest read.
            trades.forEach(t -> byId.put(t.id(), t));

            boolean allTerminal = tradeIds.stream().allMatch(
                    id -> byId.containsKey(id) && byId.get(id).status().isTerminal());
            if (allTerminal) {
                List<SettledTrade> resolved = new ArrayList<>(byId.values());
                boolean anyFailed = resolved.stream()
                        .anyMatch(t -> t.status().is(TradeStatus.Known.FAILED));
                return anyFailed
                        ? new ReconciliationOutcome.Failed(resolved)
                        : new ReconciliationOutcome.Confirmed(resolved);
            }
            if (!clock.instant().isBefore(deadline)) {
                return new ReconciliationOutcome.Pending(orderId, tradeIds, Optional.empty());
            }
            try {
                sleeper.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while reconciling trades", e);
            }
        }
    }
}
