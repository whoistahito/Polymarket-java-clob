package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/** Top-of-book update ({@code event_type: "best_bid_ask"}); needs {@code custom_feature_enabled}. */
public record BestBidAskEvent(@NonNull String assetId, @NonNull String market,
        @NonNull BigDecimal bestBid, @NonNull BigDecimal bestAsk, @NonNull BigDecimal spread,
        @NonNull String timestamp) {
}
