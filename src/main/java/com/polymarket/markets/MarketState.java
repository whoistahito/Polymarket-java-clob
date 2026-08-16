package com.polymarket.markets;

import java.util.Objects;
import java.util.Optional;

/** Published lifecycle flags. An absent flag is not {@code false}; it was never sent. */
public record MarketState(
        Optional<Boolean> active,
        Optional<Boolean> closed,
        Optional<Boolean> archived,
        Optional<Boolean> acceptingOrders,
        Optional<Boolean> orderBookEnabled) {

    public MarketState {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(closed, "closed");
        Objects.requireNonNull(archived, "archived");
        Objects.requireNonNull(acceptingOrders, "acceptingOrders");
        Objects.requireNonNull(orderBookEnabled, "orderBookEnabled");
    }
}
