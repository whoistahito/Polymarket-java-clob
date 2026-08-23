package com.polymarket.streaming;

import java.math.BigDecimal;

/** Top-of-book update ({@code event_type: "best_bid_ask"}); needs {@code custom_feature_enabled}. */
public record BestBidAskEvent(
        String assetId, String market, BigDecimal bestBid, BigDecimal bestAsk, BigDecimal spread,
        String timestamp) {
}
