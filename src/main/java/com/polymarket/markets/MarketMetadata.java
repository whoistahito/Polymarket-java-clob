package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptive discovery figures. {@code minimumOrderNotional} is Gamma's {@code orderMinSize},
 * labelled USDC notional — never the CLOB minimum-share rule that signing must use.
 */
public record MarketMetadata(
        Optional<BigDecimal> liquidity,
        Optional<BigDecimal> volume,
        Optional<BigDecimal> minimumOrderNotional,
        List<MarketTag> tags) {

    public MarketMetadata {
        Objects.requireNonNull(liquidity, "liquidity");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(minimumOrderNotional, "minimumOrderNotional");
        tags = List.copyOf(tags);
    }
}
