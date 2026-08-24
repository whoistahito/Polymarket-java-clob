package com.polymarket.portfolio;

import java.util.Optional;
import lombok.NonNull;

/**
 * Resolution state of a Combo position or one of its legs. The raw value is kept, so a state
 * added after this release still reads instead of failing the whole page.
 */
public record ComboStatus(@NonNull String raw) {

    /** data-openapi.yaml ComboPosition.status / ComboLeg.leg_status, read 2026-08-23. */
    public enum Known {
        OPEN, PARTIAL, RESOLVED_PARTIAL, RESOLVED_WIN, RESOLVED_LOSS
    }

    public Optional<Known> known() {
        for (Known candidate : Known.values()) {
            if (candidate.name().equalsIgnoreCase(raw)) return Optional.of(candidate);
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
