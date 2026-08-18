package com.polymarket.streaming;

import java.math.BigDecimal;

/** One resting price level inside a {@link BookEvent} snapshot. */
public record BookLevel(BigDecimal price, BigDecimal size) {
}
