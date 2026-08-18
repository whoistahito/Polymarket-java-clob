package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * User order-update message ({@code event_type: "order"}), authenticated-channel only. Covers
 * placement, partial fills, and cancellations.
 */
public record OrderEvent(
        String id,
        String market,
        String assetId,
        String side,
        BigDecimal price,
        String type,
        Optional<String> outcome,
        Optional<String> owner,
        Optional<String> orderOwner,
        Optional<BigDecimal> originalSize,
        Optional<BigDecimal> sizeMatched,
        Optional<String> timestamp,
        List<String> associatedTrades,
        Optional<String> status,
        Optional<String> createdAt,
        Optional<String> expiration,
        Optional<String> orderType,
        Optional<String> makerAddress) {

    public OrderEvent {
        outcome = outcome == null ? Optional.empty() : outcome;
        owner = owner == null ? Optional.empty() : owner;
        orderOwner = orderOwner == null ? Optional.empty() : orderOwner;
        originalSize = originalSize == null ? Optional.empty() : originalSize;
        sizeMatched = sizeMatched == null ? Optional.empty() : sizeMatched;
        timestamp = timestamp == null ? Optional.empty() : timestamp;
        associatedTrades = associatedTrades == null ? List.of() : List.copyOf(associatedTrades);
        status = status == null ? Optional.empty() : status;
        createdAt = createdAt == null ? Optional.empty() : createdAt;
        expiration = expiration == null ? Optional.empty() : expiration;
        orderType = orderType == null ? Optional.empty() : orderType;
        makerAddress = makerAddress == null ? Optional.empty() : makerAddress;
    }
}
