package com.polymarket.markets;

import java.math.BigDecimal;

/** A number of outcome shares, exact to the protocol's six decimals. */
public final class ShareQuantity {

    private final BigDecimal value;

    private ShareQuantity(BigDecimal value) {
        this.value = value;
    }

    public static ShareQuantity of(String value) {
        return new ShareQuantity(BaseUnits.require(value, "share quantity"));
    }

    public static ShareQuantity of(BigDecimal value) {
        return new ShareQuantity(BaseUnits.require(value, "share quantity"));
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
        return o instanceof ShareQuantity other && value.compareTo(other.value) == 0;
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
