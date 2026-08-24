package com.polymarket.operations;

import java.util.Optional;
import lombok.NonNull;

/**
 * Whether one service answered its probe. A probe that fails to connect is unhealthy
 * data, not an exception, so a deployment check can report every service.
 */
public record ServiceHealth(@NonNull PolymarketService service, boolean available, @NonNull Optional<String> detail) {


    public static ServiceHealth up(PolymarketService service) {
        return new ServiceHealth(service, true, Optional.empty());
    }

    public static ServiceHealth down(PolymarketService service, String detail) {
        return new ServiceHealth(service, false, Optional.ofNullable(detail));
    }
}
