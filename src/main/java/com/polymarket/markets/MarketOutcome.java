package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** One outcome of a market, with the discovery price and CLOB token id it was published with. */
public record MarketOutcome(String name, Optional<BigDecimal> price, Optional<String> tokenId) {

    public MarketOutcome {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(tokenId, "tokenId");
    }
}
