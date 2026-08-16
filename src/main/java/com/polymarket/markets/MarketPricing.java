package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Prices as Gamma last published them. Discovery only — signing reads the live CLOB book. */
public record MarketPricing(
        Optional<BigDecimal> bestBid,
        Optional<BigDecimal> bestAsk,
        Optional<BigDecimal> lastTradePrice,
        Optional<BigDecimal> spread) {

    public MarketPricing {
        Objects.requireNonNull(bestBid, "bestBid");
        Objects.requireNonNull(bestAsk, "bestAsk");
        Objects.requireNonNull(lastTradePrice, "lastTradePrice");
        Objects.requireNonNull(spread, "spread");
    }
}
