package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Descriptive discovery figures. {@code minimumOrderNotional} is Gamma's {@code orderMinSize},
 * labelled USDC notional — never the CLOB minimum-share rule that signing must use.
 */
public record MarketMetadata(
        @NonNull Optional<BigDecimal> liquidity,
        Optional<BigDecimal> volume,
        Optional<BigDecimal> minimumOrderNotional,
        List<MarketTag> tags) {

    public MarketMetadata {
        tags = List.copyOf(tags);
    }
}
