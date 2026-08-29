package com.polymarket.markets;

import java.util.Optional;
import lombok.NonNull;

/** Published lifecycle flags. An absent flag is not {@code false}; it was never sent. */
public record MarketState(@NonNull Optional<Boolean> active, @NonNull Optional<Boolean> closed,
        @NonNull Optional<Boolean> archived, @NonNull Optional<Boolean> acceptingOrders,
        @NonNull Optional<Boolean> orderBookEnabled) {

}
