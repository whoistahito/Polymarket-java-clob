package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** One outcome of a market, with the discovery price and CLOB token id it was published with. */
public record MarketOutcome(@NonNull String name, @NonNull Optional<BigDecimal> price, Optional<String> tokenId) {

}
