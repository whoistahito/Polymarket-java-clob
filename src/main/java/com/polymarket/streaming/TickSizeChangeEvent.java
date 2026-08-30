package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/** Tick-size change notification ({@code event_type: "tick_size_change"}). */
public record TickSizeChangeEvent(@NonNull String assetId, @NonNull String market,
        @NonNull BigDecimal oldTickSize, @NonNull BigDecimal newTickSize, @NonNull String timestamp
        ) {
}
