package com.polymarket.portfolio;

import java.util.Optional;
import lombok.NonNull;

/** How long an order may rest, as the server reported it. The raw value always survives. */
public record OrderLifetime(@NonNull String raw) {

    /** clob-openapi.yaml OpenOrder.order_type, read 2026-08-23. */
    public enum Known {
        GTC, FOK, GTD, FAK
    }

    public Optional<Known> known() {
        for (Known candidate : Known.values()) {
            if (candidate.name().equals(raw)) return Optional.of(candidate);
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
