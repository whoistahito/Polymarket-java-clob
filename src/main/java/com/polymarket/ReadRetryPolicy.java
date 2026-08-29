package com.polymarket;

import java.time.Duration;
import lombok.NonNull;

/**
 * Bounded retry budget for idempotent reads only. Writes are never replayed, so no
 * policy value can enable order resubmission.
 */
public record ReadRetryPolicy(int maxAttempts, @NonNull Duration initialBackoff,
        @NonNull Duration maxBackoff) {

    public ReadRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        if (initialBackoff.isNegative() || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("backoff must not be negative");
        }
    }

    public static ReadRetryPolicy defaults() {
        return new ReadRetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    public static ReadRetryPolicy none() {
        return new ReadRetryPolicy(1, Duration.ZERO, Duration.ZERO);
    }
}
