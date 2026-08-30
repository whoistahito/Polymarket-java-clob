package com.polymarket.markets;

import java.math.BigDecimal;
import lombok.NonNull;

/**
 * A probability price in [0, 1], exact. Order bounds and the tick grid belong to
 * {@link MarketRules}, not here.
 */
public final class Price implements Comparable<Price> {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private final BigDecimal value;

    private Price(BigDecimal value) {
        this.value = value;
    }

    public static Price of(@NonNull String value) {
        return of(new BigDecimal(value));
    }

    public static Price of(@NonNull BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("price must be within [0, 1], got " + value);
        }
        return new Price(value);
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public int compareTo(Price other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Price other && value.compareTo(other.value) == 0;
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
