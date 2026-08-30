package com.polymarket.streaming;

import java.util.List;
import lombok.NonNull;

/**
 * Full or incremental order-book snapshot ({@code event_type: "book"}). Delivered on first
 * subscribing to an asset and again after each trade.
 */
public record BookEvent(@NonNull String assetId, @NonNull String market, @NonNull String timestamp,
        @NonNull List<BookLevel> bids, @NonNull List<BookLevel> asks, @NonNull String hash) {

    public BookEvent {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
