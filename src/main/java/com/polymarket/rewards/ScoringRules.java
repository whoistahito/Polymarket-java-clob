package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** What an order must satisfy to score maker rewards on a market. */
public record ScoringRules(Optional<BigDecimal> maxSpread, Optional<BigDecimal> minSize) {

    public ScoringRules {
        Objects.requireNonNull(maxSpread, "maxSpread");
        Objects.requireNonNull(minSize, "minSize");
    }
}
