package com.polymarket.portfolio;

import java.util.Objects;
import java.util.Optional;

/**
 * What an activity row records. The raw wire value is kept, so a type added after this
 * release still reads instead of failing the whole page.
 */
public record ActivityKind(String raw) {

    /** The types the Data API documented on 2026-08-16. */
    public enum Known {
        TRADE, SPLIT, MERGE, REDEEM, REWARD, CONVERSION, DEPOSIT, WITHDRAWAL, YIELD,
        MAKER_REBATE, TAKER_REBATE, REFERRAL_REWARD
    }

    public ActivityKind {
        Objects.requireNonNull(raw, "raw");
    }

    public static ActivityKind of(Known known) {
        return new ActivityKind(Objects.requireNonNull(known, "known").name());
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
