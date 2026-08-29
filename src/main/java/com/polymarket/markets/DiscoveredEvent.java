package com.polymarket.markets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A group of related markets as discovery published it. */
public record DiscoveredEvent(@NonNull String id, @NonNull Optional<String> ticker,
        @NonNull Optional<String> slug, @NonNull Optional<String> title,
        @NonNull Optional<Instant> startsAt, @NonNull Optional<Instant> endsAt,
        @NonNull Optional<Boolean> negRisk, @NonNull List<DiscoveredMarket> markets) {

    public DiscoveredEvent {
        markets = List.copyOf(markets);
    }
}
