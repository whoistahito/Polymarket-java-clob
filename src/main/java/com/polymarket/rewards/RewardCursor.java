package com.polymarket.rewards;

import java.util.Objects;
import java.util.Optional;

/**
 * A CLOB reward pagination cursor. It travels in the documented {@code next_cursor} query
 * parameter, never in a header, and only ever advances.
 */
public final class RewardCursor {

    // Documented CLOB sentinels: "MA==" opens a walk, "LTE=" marks the last page.
    private static final String FIRST_PAGE = "MA==";
    private static final String LAST_PAGE = "LTE=";

    private final String value;

    private RewardCursor(String value) {
        this.value = value;
    }

    public static RewardCursor first() {
        return new RewardCursor(FIRST_PAGE);
    }

    public static RewardCursor of(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("cursor must not be blank");
        if (LAST_PAGE.equals(value)) {
            throw new IllegalArgumentException("LTE= is the end of the walk, not a page to request");
        }
        return new RewardCursor(value);
    }

    /** Empty when the server ended the walk, said nothing, or handed back the cursor just sent. */
    public static Optional<RewardCursor> next(RewardCursor sent, String raw) {
        Objects.requireNonNull(sent, "sent");
        if (raw == null || raw.isBlank() || LAST_PAGE.equals(raw) || sent.value.equals(raw)) {
            return Optional.empty();
        }
        return Optional.of(new RewardCursor(raw));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RewardCursor cursor && value.equals(cursor.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
