package com.polymarket.trading;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;

/**
 * Disposition of one {@code DELETE /orders}. Cancellation is not a boolean: a lost response
 * leaves the orders in an unknown state, which is not the same as nothing having been cancelled.
 */
public sealed interface CancellationOutcome {

    /** The exchange answered. Stated refusals and identifiers it never mentioned stay separate. */
    record Completed(@NonNull List<String> canceled, @NonNull Map<String, String> notCanceled,
            @NonNull List<String> unaccounted) implements CancellationOutcome {

        public Completed {
            canceled = List.copyOf(canceled);
            notCanceled = Map.copyOf(notCanceled);
            unaccounted = List.copyOf(unaccounted);
        }

        public boolean isCanceled(@NonNull String orderId) {
            return canceled.contains(orderId);
        }
    }

    /** Indeterminate: transport loss, a non-success status, or a malformed success body. */
    record Uncertain(@NonNull Optional<Integer> httpStatus, @NonNull String reason,
            @NonNull Optional<Throwable> cause) implements CancellationOutcome {
    }
}
