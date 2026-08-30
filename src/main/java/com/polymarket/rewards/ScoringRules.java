package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** What an order must satisfy to score maker rewards on a market. */
public record ScoringRules(@NonNull Optional<BigDecimal> maxSpread,
        @NonNull Optional<BigDecimal> minSize) {

}
