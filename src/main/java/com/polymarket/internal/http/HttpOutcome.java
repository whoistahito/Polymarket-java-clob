package com.polymarket.internal.http;

import java.time.Duration;

/** One completed HTTP exchange. Internal: never returned from a public signature. */
public record HttpOutcome(int status, String body, Duration retryAfter) {

    public boolean successful() {
        return status >= 200 && status < 300;
    }

    /** Documented transient statuses; everything else is a definitive answer. */
    public boolean retryable() {
        return status == 425 || status == 429 || status >= 500;
    }
}
