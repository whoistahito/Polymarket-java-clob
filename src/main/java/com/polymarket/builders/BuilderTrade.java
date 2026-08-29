package com.polymarket.builders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/**
 * One settled trade attributed to the builder, exactly as the CLOB recorded it. Everything the
 * BuilderTrade schema marks required is required here; only err_msg, createdAt and updatedAt
 * are optional.
 */
public record BuilderTrade(
        @NonNull String id,
        @NonNull String tradeType,
        @NonNull String takerOrderHash,
        @NonNull String builder,
        @NonNull String market,
        @NonNull String assetId,
        @NonNull Side side,
        @NonNull BigDecimal size,
        @NonNull BigDecimal sizeUsdc,
        @NonNull BigDecimal price,
        @NonNull String status,
        @NonNull String outcome,
        int outcomeIndex,
        @NonNull String owner,
        @NonNull String maker,
        @NonNull String transactionHash,
        @NonNull Instant matchTime,
        int bucketIndex,
        @NonNull BigDecimal fee,
        @NonNull BigDecimal feeUsdc,
        @NonNull Optional<String> errorMessage,
        @NonNull Optional<Instant> createdAt,
        @NonNull Optional<Instant> updatedAt) {
}
