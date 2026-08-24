package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/**
 * An ABSOLUTE holding at {@code observedAt}. Size is reported as the API sent it: a decrease
 * or a zero is a real sell, so nothing here is clamped against an earlier snapshot.
 */
public record PositionSnapshot(
        @NonNull String asset,
        @NonNull String conditionId,
        @NonNull Optional<String> proxyWallet,
        @NonNull BigDecimal size,
        @NonNull Optional<Boolean> redeemable,
        @NonNull Optional<Boolean> mergeable,
        @NonNull Optional<Boolean> negativeRisk,
        @NonNull Optional<Instant> endsAt,
        @NonNull PositionValuation valuation,
        @NonNull MarketReference market,
        @NonNull Instant observedAt) {
}
