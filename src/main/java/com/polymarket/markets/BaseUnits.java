package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.Objects;

/** Shared six-decimal validation for the protocol's collateral and share units. */
final class BaseUnits {

    static final int DECIMALS = 6;
    private static final BigDecimal SCALE = BigDecimal.TEN.pow(DECIMALS);

    private BaseUnits() {
    }

    static BigDecimal require(String value, String field) {
        return require(new BigDecimal(Objects.requireNonNull(value, field)), field);
    }

    static BigDecimal require(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative, got " + value);
        }
        if (value.stripTrailingZeros().scale() > DECIMALS) {
            throw new IllegalArgumentException(
                    field + " cannot be represented in " + DECIMALS + " decimals: " + value);
        }
        return value;
    }

    static long toBaseUnits(BigDecimal value) {
        return value.multiply(SCALE).longValueExact();
    }
}
