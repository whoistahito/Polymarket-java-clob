package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.NonNull;

/**
 * One of the six documented price grids, matched by numeric value. There is deliberately no
 * fallback: guessing the grid mis-prices every order on the market.
 */
public final class TickSize {

    private static final List<BigDecimal> SUPPORTED = List.of(
            new BigDecimal("0.1"), new BigDecimal("0.01"), new BigDecimal("0.005"),
            new BigDecimal("0.0025"), new BigDecimal("0.001"), new BigDecimal("0.0001"));

    // Official "Choose a Price and Size" table: price, size and amount decimals per tick.
    private static final Map<String, int[]> PRECISION = Map.of(
            "0.1", new int[] {1, 2, 3},
            "0.01", new int[] {2, 2, 4},
            "0.005", new int[] {3, 2, 5},
            "0.0025", new int[] {4, 2, 6},
            "0.001", new int[] {3, 2, 5},
            "0.0001", new int[] {4, 2, 6});

    private final BigDecimal value;

    private TickSize(BigDecimal value) {
        this.value = value;
    }

    public static TickSize of(@NonNull String value) {
        return of(new BigDecimal(value));
    }

    public static TickSize of(@NonNull BigDecimal value) {
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

    public boolean isOnGrid(@NonNull Price price) {
        return price.value().remainder(value).compareTo(BigDecimal.ZERO) == 0;
    }

    /** Documented decimals a price on this grid may carry. */
    public int priceDecimals() {
        return precision()[0];
    }

    /** Documented decimals a share quantity on this grid may carry. */
    public int sizeDecimals() {
        return precision()[1];
    }

    /** Documented decimals the pUSD leg on this grid may carry. */
    public int amountDecimals() {
        return precision()[2];
    }

    private int[] precision() {
        return PRECISION.get(value.stripTrailingZeros().toPlainString());
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
