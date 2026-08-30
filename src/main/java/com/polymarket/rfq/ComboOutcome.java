package com.polymarket.rfq;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** One side of a Combo-eligible market: the leg Position ID an RFQ names, plus its last price. */
public record ComboOutcome(
        @NonNull String label,
        @NonNull com.polymarket.markets.PositionId positionId,
        @NonNull Optional<BigDecimal> price) {
}
