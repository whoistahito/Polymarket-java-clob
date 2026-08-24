package com.polymarket.trading;

import lombok.NonNull;

/** One order within a batch submission, paired with its own submission-time attributes. */
public record BatchItem(@NonNull SignedOrder order, @NonNull OrderPlacement placement) {
}
