package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** One resting order as the CLOB reported it. Sizes stay exactly as sent, in wire units. */
public record OpenOrder(
        @NonNull String id,
        @NonNull OrderStatus status,
        @NonNull Optional<String> owner,
        @NonNull Optional<String> makerAddress,
        @NonNull String market,
        @NonNull String assetId,
        @NonNull TradedSide side,
        @NonNull BigDecimal originalSize,
        @NonNull BigDecimal sizeMatched,
        @NonNull BigDecimal price,
        @NonNull Optional<String> outcome,
        @NonNull OrderLifetime orderType,
        @NonNull Optional<Instant> expiresAt,
        @NonNull List<String> associatedTradeIds,
        @NonNull Instant createdAt) {

    public OpenOrder {
        associatedTradeIds = List.copyOf(associatedTradeIds);
    }
}
