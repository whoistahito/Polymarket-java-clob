package com.polymarket.rfq;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * One page of the Combo markets catalog. A missing cursor is the documented end of the walk;
 * nothing here pages on the caller's behalf.
 */
public record ComboMarketPage(
        @NonNull List<ComboMarket> markets,
        @NonNull Optional<String> nextCursor) {

    public ComboMarketPage {
        markets = List.copyOf(markets);
    }
}
