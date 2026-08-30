package com.polymarket.streaming;

import java.util.List;
import lombok.NonNull;

/**
 * Price-change notification ({@code event_type: "price_change"}), possibly a batch touching
 * several assets in one frame.
 */
public record PriceChangeEvent(@NonNull String market, @NonNull String timestamp,
        @NonNull List<PriceChangeEntry> changes) {

    public PriceChangeEvent {
        changes = List.copyOf(changes);
    }
}
