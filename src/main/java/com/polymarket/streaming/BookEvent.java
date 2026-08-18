package com.polymarket.streaming;

import java.util.List;

/**
 * Full or incremental order-book snapshot ({@code event_type: "book"}). Delivered on first
 * subscribing to an asset and again after each trade.
 */
public record BookEvent(
        String assetId,
        String market,
        String timestamp,
        List<BookLevel> bids,
        List<BookLevel> asks,
        String hash) {

    public BookEvent {
        bids = bids == null ? List.of() : List.copyOf(bids);
        asks = asks == null ? List.of() : List.copyOf(asks);
    }
}
