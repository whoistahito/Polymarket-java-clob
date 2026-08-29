package com.polymarket.markets;

import java.util.Optional;
import lombok.NonNull;

/** Published lifecycle flags. An absent flag is not {@code false}; it was never sent. */
public record MarketState(
        @NonNull Optional<Boolean> active,
        Optional<Boolean> closed,
        Optional<Boolean> archived,
        Optional<Boolean> acceptingOrders,
        Optional<Boolean> orderBookEnabled) {

}
