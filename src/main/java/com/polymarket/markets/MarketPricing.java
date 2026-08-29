package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** Prices as Gamma last published them. Discovery only — signing reads the live CLOB book. */
public record MarketPricing(
        @NonNull Optional<BigDecimal> bestBid,
        Optional<BigDecimal> bestAsk,
        Optional<BigDecimal> lastTradePrice,
        Optional<BigDecimal> spread) {

}
