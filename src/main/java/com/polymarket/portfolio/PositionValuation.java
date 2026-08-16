package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Money attached to a position, exact and never fabricated. {@code grossInitialValue} includes
 * attributed BUY fees; {@code initialValue} and {@code averagePrice} stay fee-exclusive.
 */
public record PositionValuation(
        Optional<BigDecimal> averagePrice,
        Optional<BigDecimal> currentPrice,
        Optional<BigDecimal> initialValue,
        Optional<BigDecimal> grossInitialValue,
        Optional<BigDecimal> entryFeesUsdc,
        Optional<BigDecimal> currentValue,
        Optional<BigDecimal> totalBought,
        Optional<BigDecimal> cashPnl,
        Optional<BigDecimal> percentPnl,
        Optional<BigDecimal> realizedPnl,
        Optional<BigDecimal> percentRealizedPnl) {

    public PositionValuation {
        Objects.requireNonNull(averagePrice, "averagePrice");
        Objects.requireNonNull(currentPrice, "currentPrice");
        Objects.requireNonNull(initialValue, "initialValue");
        Objects.requireNonNull(grossInitialValue, "grossInitialValue");
        Objects.requireNonNull(entryFeesUsdc, "entryFeesUsdc");
        Objects.requireNonNull(currentValue, "currentValue");
        Objects.requireNonNull(totalBought, "totalBought");
        Objects.requireNonNull(cashPnl, "cashPnl");
        Objects.requireNonNull(percentPnl, "percentPnl");
        Objects.requireNonNull(realizedPnl, "realizedPnl");
        Objects.requireNonNull(percentRealizedPnl, "percentRealizedPnl");
    }
}
