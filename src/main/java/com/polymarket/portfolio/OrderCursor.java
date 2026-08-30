package com.polymarket.portfolio;

import java.util.Optional;
import lombok.NonNull;

/**
 * A CLOB pagination cursor. It travels in the documented {@code next_cursor} query parameter,
 * never a header, and only ever advances.
 */
public final class OrderCursor {

    // Documented CLOB sentinels: "MA==" opens a walk, "LTE=" marks the last page.
    private static final String FIRST_PAGE = "MA==";
    private static final String LAST_PAGE = "LTE=";

    private final String value;

    private OrderCursor(String value) {
        this.value = value;
    }

    public static OrderCursor first() {
        return new OrderCursor(FIRST_PAGE);
    }

    public static OrderCursor of(@NonNull String value) {
        if (value.isBlank()) throw new IllegalArgumentException("cursor must not be blank");
        if (LAST_PAGE.equals(value)) {
            throw new IllegalArgumentException("LTE= is the end of the walk, not a page to request");
        }
        return new OrderCursor(value);
    }

    /** Empty when the server ended the walk, said nothing, or handed back the cursor just sent. */
    public static Optional<OrderCursor> next(@NonNull OrderCursor sent, String raw) {
        if (raw == null || raw.isBlank() || LAST_PAGE.equals(raw) || sent.value.equals(raw)) {
            return Optional.empty();
        }
        return Optional.of(new OrderCursor(raw));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrderCursor cursor && value.equals(cursor.value);
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
