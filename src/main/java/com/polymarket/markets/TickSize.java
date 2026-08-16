package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * One of the six documented price grids, matched by numeric value. There is deliberately no
 * fallback: guessing the grid mis-prices every order on the market.
 */
public final class TickSize {

    private static final List<BigDecimal> SUPPORTED = List.of(
            new BigDecimal("0.1"), new BigDecimal("0.01"), new BigDecimal("0.005"),
            new BigDecimal("0.0025"), new BigDecimal("0.001"), new BigDecimal("0.0001"));

    private final BigDecimal value;

    private TickSize(BigDecimal value) {
        this.value = value;
    }

    public static TickSize of(String value) {
        return of(new BigDecimal(Objects.requireNonNull(value, "value")));
    }

    public static TickSize of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return SUPPORTED.stream()
                .filter(supported -> supported.compareTo(value) == 0)
                .findFirst()
                .map(TickSize::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported tick size " + value + "; supported grid is " + SUPPORTED));
    }

    public BigDecimal value() {
        return value;
    }

    public boolean isOnGrid(Price price) {
        return price.value().remainder(value).compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TickSize other && value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
