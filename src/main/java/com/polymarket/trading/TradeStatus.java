package com.polymarket.trading;

import java.util.Objects;
import java.util.Optional;

/** A trade's status as the server sent it; the raw value survives a status this release doesn't know. */
public record TradeStatus(String raw) {

    public enum Known {
        MATCHED, MINED, CONFIRMED, RETRYING, FAILED
    }

    public TradeStatus {
        Objects.requireNonNull(raw, "raw");
    }

    public Optional<Known> known() {
        for (Known candidate : Known.values()) {
            if (candidate.name().equalsIgnoreCase(raw)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public boolean is(Known known) {
        return known().filter(known::equals).isPresent();
    }

    /** CONFIRMED or FAILED: the trade will not change state on a later read. */
    public boolean isTerminal() {
        return is(Known.CONFIRMED) || is(Known.FAILED);
    }
}
