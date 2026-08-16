package com.polymarket.markets;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One live book read: levels, rules, negative-risk state, hash and time. Sufficient on its
 * own for signing and immediate-order planning, so no second lookup is needed.
 */
public record OrderBookSnapshot(
        String conditionId,
        AssetId asset,
        Instant observedAt,
        String hash,
        List<PriceLevel> bids,
        List<PriceLevel> asks,
        MarketRules rules,
        Optional<Price> lastTradePrice) {

    public OrderBookSnapshot {
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(rules, "rules");
        // A market that has never traded reports "" on the wire: absent, not zero.
        Objects.requireNonNull(lastTradePrice, "lastTradePrice");
        // Official sources disagree about wire ordering, so sort numerically before any use.
        bids = sorted(bids, Comparator.comparing(PriceLevel::price).reversed());
        asks = sorted(asks, Comparator.comparing(PriceLevel::price));
    }

    /** Highest bid: what a SELL can hit first. */
    public Optional<PriceLevel> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.get(0));
    }

    /** Lowest ask: what a BUY can lift first. */
    public Optional<PriceLevel> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.get(0));
    }

    private static List<PriceLevel> sorted(List<PriceLevel> levels, Comparator<PriceLevel> order) {
        return levels == null ? List.of() : levels.stream().sorted(order).toList();
    }
}
