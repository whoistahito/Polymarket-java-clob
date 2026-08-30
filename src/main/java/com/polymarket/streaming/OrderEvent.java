package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * User order-update message ({@code event_type: "order"}), authenticated-channel only. Covers
 * placement, partial fills, and cancellations.
 */
public record OrderEvent(@NonNull String id, @NonNull String market, @NonNull String assetId,
        @NonNull String side, @NonNull BigDecimal price, @NonNull String type,
        @NonNull Optional<String> outcome, @NonNull Optional<String> owner,
        @NonNull Optional<String> orderOwner, @NonNull Optional<BigDecimal> originalSize,
        @NonNull Optional<BigDecimal> sizeMatched, @NonNull Optional<String> timestamp,
        @NonNull List<String> associatedTrades, @NonNull Optional<String> status,
        @NonNull Optional<String> createdAt, @NonNull Optional<String> expiration,
        @NonNull Optional<String> orderType, @NonNull Optional<String> makerAddress) {

    public OrderEvent {
        associatedTrades = List.copyOf(associatedTrades);
    }
}
