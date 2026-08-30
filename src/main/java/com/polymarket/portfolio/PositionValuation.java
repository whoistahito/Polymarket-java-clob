package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/**
 * Money attached to a position, exact and never fabricated. {@code grossInitialValue} includes
 * attributed BUY fees; {@code initialValue} and {@code averagePrice} stay fee-exclusive.
 */
public record PositionValuation(
        @NonNull Optional<BigDecimal> averagePrice,
        @NonNull Optional<BigDecimal> currentPrice,
        @NonNull Optional<BigDecimal> initialValue,
        @NonNull Optional<BigDecimal> grossInitialValue,
        @NonNull Optional<BigDecimal> entryFeesUsdc,
        @NonNull Optional<BigDecimal> currentValue,
        @NonNull Optional<BigDecimal> totalBought,
        @NonNull Optional<BigDecimal> cashPnl,
        @NonNull Optional<BigDecimal> percentPnl,
        @NonNull Optional<BigDecimal> realizedPnl,
        @NonNull Optional<BigDecimal> percentRealizedPnl) {
}
