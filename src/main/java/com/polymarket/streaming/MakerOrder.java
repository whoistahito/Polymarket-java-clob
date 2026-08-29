package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** Maker-order details nested inside a {@link TradeEvent}. */
public record MakerOrder(@NonNull String assetId, @NonNull Optional<BigDecimal> matchedAmount,
        @NonNull String orderId, @NonNull Optional<String> outcome,
        @NonNull Optional<Integer> outcomeIndex, @NonNull String side,
        @NonNull Optional<String> owner, @NonNull Optional<String> makerAddress,
        @NonNull BigDecimal price, @NonNull Optional<String> feeRateBps) {
}
