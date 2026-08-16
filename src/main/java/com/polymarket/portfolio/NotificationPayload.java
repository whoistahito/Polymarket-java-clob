package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * The order-lifecycle payload the CLOB API documents. Fields a payload does not carry stay
 * absent; nothing here degrades into an untyped map.
 */
public record NotificationPayload(
        Optional<String> orderId,
        Optional<String> market,
        Optional<String> assetId,
        Optional<TradedSide> side,
        Optional<BigDecimal> price,
        Optional<BigDecimal> originalSize,
        Optional<BigDecimal> matchedSize,
        Optional<BigDecimal> remainingSize,
        Optional<String> outcome,
        Optional<Integer> outcomeIndex) {

    public NotificationPayload {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(originalSize, "originalSize");
        Objects.requireNonNull(matchedSize, "matchedSize");
        Objects.requireNonNull(remainingSize, "remainingSize");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(outcomeIndex, "outcomeIndex");
    }
}
