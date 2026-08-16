package com.polymarket.portfolio;

import java.util.Objects;
import java.util.Optional;

/**
 * The side a server row reported. The raw value is kept so a value this release does not
 * know cannot break the whole read.
 */
public record TradedSide(String raw) {

    public TradedSide {
        Objects.requireNonNull(raw, "raw");
    }

    public Optional<Side> known() {
        for (Side side : Side.values()) {
            if (side.name().equals(raw)) return Optional.of(side);
        }
        return Optional.empty();
    }

    public boolean isKnown() {
        return known().isPresent();
    }

    public boolean isBuy() {
        return known().filter(Side.BUY::equals).isPresent();
    }

    public boolean isSell() {
        return known().filter(Side.SELL::equals).isPresent();
    }
}
