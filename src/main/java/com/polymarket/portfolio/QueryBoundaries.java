package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The documented shapes a Data API portfolio filter must satisfy, checked here so a malformed
 * query fails in the caller's stack frame instead of as a server 400.
 */
final class QueryBoundaries {

    // data-openapi.yaml: Address ^0x[a-fA-F0-9]{40}$, Hash64 ^0x[a-fA-F0-9]{64}$.
    private static final String ADDRESS = "0x[0-9a-fA-F]{40}";
    private static final String HASH_64 = "0x[0-9a-fA-F]{64}";
    // data-openapi.yaml ComboConditionId is bytes31, so 62 hex digits — not a 64-hex hash.
    private static final String COMBO_CONDITION_ID = "0x[0-9a-fA-F]{62}";

    private QueryBoundaries() {
    }

    static String address(String value, String field) {
        if (value == null || !value.matches(ADDRESS)) {
            throw new IllegalArgumentException(
                    field + " must be a 0x-prefixed 20-byte hex address, got: " + value);
        }
        return value;
    }

    static List<String> conditionIds(List<String> values) {
        return identifiers(values, HASH_64, "condition id must be a 0x-prefixed 32-byte hex hash");
    }

    static List<String> comboConditionIds(List<String> values) {
        return identifiers(values, COMBO_CONDITION_ID,
                "combo condition id must be a 0x-prefixed 31-byte hex value");
    }

    private static List<String> identifiers(List<String> values, String pattern, String message) {
        List<String> copy = List.copyOf(values);
        for (String id : copy) {
            if (id == null || !id.matches(pattern)) {
                throw new IllegalArgumentException(message + ", got: " + id);
            }
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        copy.forEach(id -> unique.add(id.toLowerCase(Locale.ROOT)));
        if (unique.size() != copy.size()) {
            throw new IllegalArgumentException("condition ids must not contain duplicates: " + copy);
        }
        return copy;
    }

    /** data-openapi.yaml sizeThreshold: {@code minimum: 0}. */
    static BigDecimal threshold(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("sizeThreshold must not be negative, got " + value);
        }
        return value;
    }

    /** data-openapi.yaml start/end: epoch seconds with {@code minimum: 0}. */
    static Instant windowBound(Instant value, String field) {
        if (value.getEpochSecond() < 0) {
            throw new IllegalArgumentException(
                    field + " must not be before the epoch, got " + value);
        }
        return value;
    }

    static void orderedWindow(Instant from, Instant to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("the window end " + to + " precedes its start " + from);
        }
    }
}
