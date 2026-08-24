package com.polymarket.trading;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Disposition of one {@code POST /orders} batch. Per-item outcomes are attached positionally
 * (the wire response array has no per-item ID to correlate against) and only when the response
 * is a readable array of the same length as the request — a mismatch never invents which item
 * succeeded.
 */
public sealed interface BatchSubmissionOutcome {

    record Completed(@NonNull List<SubmissionOutcome> items) implements BatchSubmissionOutcome {
        public Completed {
            items = List.copyOf(items);
        }
    }

    /** The batch as a whole could not be attributed to its items: nothing per-item is invented. */
    record Indeterminate(@NonNull String reason, @NonNull Optional<Throwable> cause)
            implements BatchSubmissionOutcome {
    }
}
