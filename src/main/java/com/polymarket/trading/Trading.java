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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;

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
     * Polls the given trade IDs until every one settles or {@code timeout} elapses. A delayed
     * transaction hash simply shows up on a later poll; a timeout is
     * {@link ReconciliationOutcome.Pending}, never a reported failure.
     */
    public ReconciliationOutcome reconcile(@NonNull ApiCredentials credentials,
            @NonNull String address, @NonNull String orderId, @NonNull List<String> tradeIds,
            @NonNull Duration timeout, @NonNull Duration pollInterval) throws IOException {
        return poll(credentials, address, orderId, Optional.empty(), tradeIds, timeout, pollInterval);
    }

    /** The Combo form: the RFQ ID travels with the outcome so a Pending stays recoverable. */
    public ReconciliationOutcome reconcile(@NonNull ApiCredentials credentials,
            @NonNull String address, @NonNull String orderId, @NonNull String rfqId,
            @NonNull List<String> tradeIds, @NonNull Duration timeout,
            @NonNull Duration pollInterval) throws IOException {
        if (rfqId.isBlank()) throw new IllegalArgumentException("rfqId must not be blank");
        return poll(credentials, address, orderId, Optional.of(rfqId), tradeIds, timeout,
                pollInterval);
    }

    private ReconciliationOutcome poll(ApiCredentials credentials, String address, String orderId,
            Optional<String> rfqId, List<String> tradeIds, Duration timeout, Duration pollInterval)
            throws IOException {
        requireReconcilable(address, orderId, tradeIds, timeout, pollInterval);
        ReconciliationOutcome pending =
                new ReconciliationOutcome.Pending(orderId, tradeIds, rfqId);
        Instant deadline = clock.instant().plus(timeout);

        while (true) {
            Optional<ReconciliationOutcome> resolved =
                    classify(tradeReader.byIds(credentials, address, tradeIds), tradeIds);
            if (resolved.isPresent()) return resolved.get();

            // The deadline binds the network work too, so a slow response cannot overshoot it.
            Instant now = clock.instant();
            if (!now.isBefore(deadline)) return pending;
            Duration remaining = Duration.between(now, deadline);
            try {
                sleeper.sleep(pollInterval.compareTo(remaining) < 0 ? pollInterval : remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while reconciling trades", e);
            }
            if (!clock.instant().isBefore(deadline)) return pending;
        }
    }

    /** Empty while the settlement is still open; otherwise the disposition it reached. */
    private static Optional<ReconciliationOutcome> classify(List<SettledTrade> read,
            List<String> tradeIds) {
        Map<String, Set<SettledTrade>> byId = new LinkedHashMap<>();
        tradeIds.forEach(id -> byId.put(id, new LinkedHashSet<>()));
        read.stream().filter(t -> byId.containsKey(t.id()))
                .forEach(t -> byId.get(t.id()).add(t));

        List<SettledTrade> records = byId.values().stream().flatMap(Set::stream).toList();
        List<String> contradictions = new ArrayList<>();
        byId.forEach((id, found) -> contradictions.addAll(contradictions(id, found)));
        if (!contradictions.isEmpty()) {
            return Optional.of(new ReconciliationOutcome.Inconsistent(records, contradictions));
        }
        if (!byId.values().stream().allMatch(
                found -> found.size() == 1 && found.iterator().next().settled())) {
            return Optional.empty();
        }
        boolean anyFailed = records.stream().anyMatch(t -> t.status().is(TradeStatus.Known.FAILED));
        return Optional.of(anyFailed
                ? new ReconciliationOutcome.Failed(records)
                : new ReconciliationOutcome.Confirmed(records));
    }

    private static List<String> contradictions(String id, Set<SettledTrade> found) {
        if (found.size() > 1) {
            return List.of("trade " + id + " was reported " + found.size()
                    + " times with records that disagree");
        }
        if (found.isEmpty()) return List.of();
        SettledTrade trade = found.iterator().next();
        List<String> problems = new ArrayList<>();
        if (trade.status().is(TradeStatus.Known.CONFIRMED) && trade.errorMessage().isPresent()) {
            problems.add("trade " + id + " is CONFIRMED yet reports an error: "
                    + trade.errorMessage().orElseThrow());
        }
        if (trade.status().is(TradeStatus.Known.FAILED) && trade.transactionHash().isPresent()) {
            problems.add("trade " + id + " is FAILED yet carries a transaction hash");
        }
        // clob-openapi.yaml marks these Trade fields required; a record without them is malformed.
        if (trade.side().isEmpty() || trade.assetId().isEmpty() || trade.size().isEmpty()
                || trade.price().isEmpty()) {
            problems.add("trade " + id + " omits a field the documented Trade schema requires");
        }
        return problems;
    }

    private static void requireReconcilable(String address, String orderId, List<String> tradeIds,
            Duration timeout, Duration pollInterval) {
        if (!address.matches("0x[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException(
                    "address must be a 0x-prefixed 20-byte hex address, got: " + address);
        }
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId must not be blank");
        if (tradeIds.isEmpty()) {
            throw new IllegalArgumentException("reconcile needs at least one trade id");
        }
        if (tradeIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("trade ids must not be blank");
        }
        if (tradeIds.size() != Set.copyOf(tradeIds).size()) {
            throw new IllegalArgumentException("trade ids must not contain duplicates");
        }
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative, got " + timeout);
        }
        if (pollInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "pollInterval must not be negative, got " + pollInterval);
        }
    }
}
