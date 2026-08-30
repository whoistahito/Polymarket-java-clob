package com.polymarket.markets;

import java.math.BigDecimal;

/** An amount of pUSD collateral, exact to the protocol's six decimals. */
public final class PusdAmount {

    private final BigDecimal value;

    private PusdAmount(BigDecimal value) {
        this.value = value;
    }

    public static PusdAmount of(String value) {
        return new PusdAmount(BaseUnits.require(value, "pUSD amount"));
    }

    public static PusdAmount of(BigDecimal value) {
        return new PusdAmount(BaseUnits.require(value, "pUSD amount"));
    }

    public BigDecimal value() {
        return value;
    }

    public long baseUnits() {
        return BaseUnits.toBaseUnits(value);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PusdAmount other && value.compareTo(other.value) == 0;
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
