package com.polymarket.markets;

import java.util.Objects;

/** One resting level of the order book. */
public record PriceLevel(Price price, ShareQuantity size) {

    public PriceLevel {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(size, "size");
    }
}
