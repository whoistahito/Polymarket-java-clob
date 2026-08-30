package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** Trading metrics a reward read publishes alongside a market. */
public record MarketMetrics(@NonNull Optional<BigDecimal> volume24hr,
        @NonNull Optional<BigDecimal> spread, @NonNull Optional<BigDecimal> competitiveness,
        @NonNull Optional<BigDecimal> oneDayPriceChange) {

}
