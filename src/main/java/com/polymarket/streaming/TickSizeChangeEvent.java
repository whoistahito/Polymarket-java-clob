package com.polymarket.streaming;

import java.math.BigDecimal;

/** Tick-size change notification ({@code event_type: "tick_size_change"}). */
public record TickSizeChangeEvent(
        String assetId, String market, BigDecimal oldTickSize, BigDecimal newTickSize, String timestamp) {
}
