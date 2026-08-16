package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Trading metrics a reward read publishes alongside a market. */
public record MarketMetrics(
        Optional<BigDecimal> volume24hr,
        Optional<BigDecimal> spread,
        Optional<BigDecimal> competitiveness,
        Optional<BigDecimal> oneDayPriceChange) {

    public MarketMetrics {
        Objects.requireNonNull(volume24hr, "volume24hr");
        Objects.requireNonNull(spread, "spread");
        Objects.requireNonNull(competitiveness, "competitiveness");
        Objects.requireNonNull(oneDayPriceChange, "oneDayPriceChange");
    }
}
