package com.polymarket.streaming;

import java.util.List;

/**
 * Price-change notification ({@code event_type: "price_change"}), possibly a batch touching
 * several assets in one frame.
 */
public record PriceChangeEvent(String market, String timestamp, List<PriceChangeEntry> changes) {

    public PriceChangeEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
