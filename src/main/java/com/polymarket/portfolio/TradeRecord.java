package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/** One settled fill. Amounts are exact; an absent field was never sent. */
public record TradeRecord(
        @NonNull Optional<String> proxyWallet,
        @NonNull TradedSide side,
        @NonNull String asset,
        @NonNull String conditionId,
        @NonNull BigDecimal size,
        @NonNull BigDecimal price,
        @NonNull Instant executedAt,
        @NonNull Optional<String> transactionHash,
        @NonNull MarketReference market) {
}
