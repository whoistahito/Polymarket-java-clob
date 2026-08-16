package com.polymarket.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A taker fee rate in basis points, supplied by the caller from live market data.
 * Fees are set at match time, so this bounds a budget rather than predicting the charge.
 */
public record FeeRate(int basisPoints) {

    private static final BigDecimal BPS = new BigDecimal("10000");

    public FeeRate {
        if (basisPoints < 0) {
            throw new IllegalArgumentException("a fee rate cannot be negative");
        }
    }

    public static FeeRate ofBasisPoints(int basisPoints) {
        return new FeeRate(basisPoints);
    }

    /** Worst-case fee on a notional, rounded UP so a budget is never understated. */
    public BigDecimal feeOn(BigDecimal notional) {
        return Objects.requireNonNull(notional, "notional")
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BPS, 6, RoundingMode.UP);
    }
}
