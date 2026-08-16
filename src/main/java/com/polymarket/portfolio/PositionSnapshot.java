package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An ABSOLUTE holding at {@code observedAt}. Size is reported as the API sent it: a decrease
 * or a zero is a real sell, so nothing here is clamped against an earlier snapshot.
 */
public record PositionSnapshot(
        String asset,
        String conditionId,
        Optional<String> proxyWallet,
        BigDecimal size,
        Optional<Boolean> redeemable,
        Optional<Boolean> mergeable,
        Optional<Boolean> negativeRisk,
        Optional<Instant> endsAt,
        PositionValuation valuation,
        MarketReference market,
        Instant observedAt) {

    public PositionSnapshot {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(proxyWallet, "proxyWallet");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(redeemable, "redeemable");
        Objects.requireNonNull(mergeable, "mergeable");
        Objects.requireNonNull(negativeRisk, "negativeRisk");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(valuation, "valuation");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
