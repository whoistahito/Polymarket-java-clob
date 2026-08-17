package com.polymarket.trading;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Disposition of one {@code POST /order} submission. Placement is not a boolean: the exchange
 * gives no exactly-once guarantee, so accepted/rejected/unknown are genuinely different outcomes.
 */
public sealed interface SubmissionOutcome {

    /** A coherent success: the order exists and must be reconciled, never assumed live from fills. */
    record Accepted(String orderId, String status, List<String> tradeIds,
            Optional<String> makingAmount, Optional<String> takingAmount) implements SubmissionOutcome {
        public Accepted {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(status, "status");
            tradeIds = List.copyOf(tradeIds);
        }
    }

    /** The exchange definitively refused the order before it could rest or match; nothing is live. */
    record Rejected(int httpStatus, String reason, boolean safeToRetry) implements SubmissionOutcome {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Indeterminate: transport loss, a generic 5xx, or a null/contradictory body. May or may not be live. */
    record Unknown(Optional<Integer> httpStatus, String reason, Optional<Throwable> cause)
            implements SubmissionOutcome {
        public Unknown {
            Objects.requireNonNull(httpStatus, "httpStatus");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(cause, "cause");
        }
    }
}
