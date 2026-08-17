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

/** Signing and submission are independently reachable; {@link #place} is a thin convenience over both. */
public final class Trading {

    private final OrderSigner signer;
    private final OrderSubmitter submitter;
    private final TradeReader tradeReader;
    private final Clock clock;
    private final Sleeper sleeper;

    /** Injected so a reconciliation poll loop can be tested without a real wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public Trading(OrderSigner signer, OrderSubmitter submitter, TradeReader tradeReader, Clock clock) {
        this(signer, submitter, tradeReader, clock, d -> Thread.sleep(d.toMillis()));
    }

    public Trading(OrderSigner signer, OrderSubmitter submitter, TradeReader tradeReader, Clock clock,
            Sleeper sleeper) {
        this.signer = Objects.requireNonNull(signer, "signer");
        this.submitter = Objects.requireNonNull(submitter, "submitter");
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
