package com.polymarket.trading;

import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.NonNull;

/**
 * The official taker fee coefficient: {@code fee = shares x rate x price x (1 - price)}, charged
 * at match time and never carried on the order. Makers are never charged.
 */
public record FeeRate(@NonNull BigDecimal coefficient) {

    /** Official: fees are quoted to five decimals, and anything under one unit rounds to zero. */
    public static final int FEE_DECIMALS = 5;

    /** Official: the smallest chargeable fee; anything under it rounds to zero. */
    public static final BigDecimal SMALLEST_FEE = new BigDecimal("0.00001");

    private static final BigDecimal BASIS_POINTS = new BigDecimal("10000");

    public FeeRate {
        if (coefficient.signum() < 0) {
            throw new IllegalArgumentException("a fee rate cannot be negative, got " + coefficient);
        }
    }

    /** The decimal coefficient Gamma publishes as {@code feeSchedule.rate} (0.04 - 0.07). */
    public static FeeRate of(@NonNull String coefficient) {
        return new FeeRate(new BigDecimal(coefficient));
    }

    /**
     * The CLOB's {@code GET /fee-rate} publishes the same coefficient as an integer in basis
     * points. It is converted here, never fed into the formula as-is.
     */
    public static FeeRate ofBasisPoints(int basisPoints) {
        return new FeeRate(BigDecimal.valueOf(basisPoints).divide(BASIS_POINTS));
    }

    /** The charge for one fill, at the published five-decimal precision. */
    public PusdAmount feeOn(@NonNull ShareQuantity shares, @NonNull Price price) {
        return PusdAmount.of(exactFeeOn(shares, price)
                .setScale(FEE_DECIMALS, RoundingMode.HALF_UP));
    }

    /** The unrounded charge, for accumulating a walk before it is quoted. */
    public BigDecimal exactFeeOn(@NonNull ShareQuantity shares, @NonNull Price price) {
        return coefficient.multiply(shares.value()).multiply(price.value())
                .multiply(BigDecimal.ONE.subtract(price.value()));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FeeRate other && coefficient.compareTo(other.coefficient) == 0;
    }

    @Override
    public int hashCode() {
        return coefficient.stripTrailingZeros().hashCode();
    }
}
