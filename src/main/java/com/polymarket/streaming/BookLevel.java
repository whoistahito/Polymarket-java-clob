package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/** One resting price level inside a {@link BookEvent} snapshot. */
public record BookLevel(@NonNull BigDecimal price, @NonNull BigDecimal size) {
}
