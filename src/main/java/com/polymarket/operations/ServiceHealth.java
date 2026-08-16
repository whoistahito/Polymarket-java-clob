package com.polymarket.operations;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether one service answered its probe. A probe that fails to connect is unhealthy
 * data, not an exception, so a deployment check can report every service.
 */
public record ServiceHealth(PolymarketService service, boolean available, Optional<String> detail) {

    public ServiceHealth {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(detail, "detail");
    }

    public static ServiceHealth up(PolymarketService service) {
        return new ServiceHealth(service, true, Optional.empty());
    }

    public static ServiceHealth down(PolymarketService service, String detail) {
        return new ServiceHealth(service, false, Optional.ofNullable(detail));
    }
}
