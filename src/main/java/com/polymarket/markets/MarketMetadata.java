package com.polymarket.markets;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Descriptive discovery figures. {@code minimumOrderNotional} is Gamma's {@code orderMinSize},
 * labelled USDC notional — never the CLOB minimum-share rule that signing must use.
 */
public record MarketMetadata(@NonNull Optional<BigDecimal> liquidity,
        @NonNull Optional<BigDecimal> volume, @NonNull Optional<BigDecimal> minimumOrderNotional,
        @NonNull List<MarketTag> tags) {

    public MarketMetadata {
        tags = List.copyOf(tags);
    }
}
