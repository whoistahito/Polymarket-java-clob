package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** Trading metrics a reward read publishes alongside a market. */
public record MarketMetrics(
        @NonNull Optional<BigDecimal> volume24hr,
        Optional<BigDecimal> spread,
        Optional<BigDecimal> competitiveness,
        Optional<BigDecimal> oneDayPriceChange) {

}
