package com.polymarket.portfolio;

import java.util.Locale;
import java.util.Optional;
import lombok.NonNull;

/**
 * An open order's status. The wire value keeps its documented {@code ORDER_STATUS_} prefix, so a
 * status added after this release still reads instead of failing the whole page.
 */
public record OrderStatus(@NonNull String raw) {

    /** clob-openapi.yaml OpenOrder.status, read 2026-08-23. */
    public enum Known {
        LIVE, INVALID, CANCELED_MARKET_RESOLVED, CANCELED, MATCHED
    }

    private static final String WIRE_PREFIX = "ORDER_STATUS_";

    public Optional<Known> known() {
        String bare = raw.toUpperCase(Locale.ROOT);
        if (bare.startsWith(WIRE_PREFIX)) bare = bare.substring(WIRE_PREFIX.length());
        for (Known candidate : Known.values()) {
            if (candidate.name().equals(bare)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public boolean isKnown() {
        return known().isPresent();
    }

    public boolean is(Known known) {
        return known().filter(known::equals).isPresent();
    }
}
