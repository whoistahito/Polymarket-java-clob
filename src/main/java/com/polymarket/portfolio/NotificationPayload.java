package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/**
 * The order-lifecycle payload the CLOB API documents. Fields a payload does not carry stay
 * absent; nothing here degrades into an untyped map.
 */
public record NotificationPayload(
        @NonNull Optional<String> orderId,
        @NonNull Optional<String> market,
        @NonNull Optional<String> assetId,
        @NonNull Optional<TradedSide> side,
        @NonNull Optional<BigDecimal> price,
        @NonNull Optional<BigDecimal> originalSize,
        @NonNull Optional<BigDecimal> matchedSize,
        @NonNull Optional<BigDecimal> remainingSize,
        @NonNull Optional<String> outcome,
        @NonNull Optional<Integer> outcomeIndex) {
}
