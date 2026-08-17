package com.polymarket.trading;

import java.util.Objects;

/** One order within a batch submission, paired with its own submission-time attributes. */
public record BatchItem(SignedOrder order, OrderPlacement placement) {

    public BatchItem {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(placement, "placement");
    }
}
