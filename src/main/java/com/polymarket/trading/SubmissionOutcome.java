package com.polymarket.trading;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Disposition of one {@code POST /order} submission. Placement is not a boolean: the exchange
 * gives no exactly-once guarantee, so accepted/rejected/unknown are genuinely different outcomes.
 */
public sealed interface SubmissionOutcome {

    /** A coherent success: the order exists and must be reconciled, never assumed live from fills. */
    record Accepted(@NonNull String orderId, @NonNull String status, @NonNull List<String> tradeIds,
            @NonNull List<String> transactionHashes, @NonNull Optional<String> makingAmount,
            @NonNull Optional<String> takingAmount) implements SubmissionOutcome {
        public Accepted {
            tradeIds = List.copyOf(tradeIds);
            transactionHashes = List.copyOf(transactionHashes);
        }
    }

    /** The exchange definitively refused the order before it could rest or match; nothing is live. */
    record Rejected(int httpStatus, @NonNull String reason, boolean safeToRetry)
            implements SubmissionOutcome {
    }

    /** Indeterminate: transport loss, a generic 5xx, or a null/contradictory body. May or may not be live. */
    record Unknown(@NonNull Optional<Integer> httpStatus, @NonNull String reason,
            @NonNull Optional<Throwable> cause) implements SubmissionOutcome {
    }
}
