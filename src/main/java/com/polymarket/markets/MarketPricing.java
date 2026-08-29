package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** Prices as Gamma last published them. Discovery only — signing reads the live CLOB book. */
public record MarketPricing(@NonNull Optional<BigDecimal> bestBid,
        @NonNull Optional<BigDecimal> bestAsk, @NonNull Optional<BigDecimal> lastTradePrice,
        @NonNull Optional<BigDecimal> spread) {

}
