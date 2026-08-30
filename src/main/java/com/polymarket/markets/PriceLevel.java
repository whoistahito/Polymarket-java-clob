package com.polymarket.markets;

import lombok.NonNull;

/** One resting level of the order book. */
public record PriceLevel(@NonNull Price price, @NonNull ShareQuantity size) {

}
