package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/**
 * One account-history row. Deposits and conversions are not market-scoped, so the market
 * fields are optional rather than blanked out.
 */
public record ActivityRecord(
        @NonNull Optional<String> proxyWallet,
        @NonNull ActivityKind kind,
        @NonNull Instant occurredAt,
        @NonNull Optional<String> conditionId,
        @NonNull Optional<String> asset,
        @NonNull Optional<BigDecimal> size,
        @NonNull Optional<BigDecimal> usdcSize,
        @NonNull Optional<BigDecimal> price,
        @NonNull Optional<TradedSide> side,
        @NonNull Optional<String> transactionHash,
        @NonNull Optional<Boolean> combo,
        @NonNull MarketReference market) {
}
