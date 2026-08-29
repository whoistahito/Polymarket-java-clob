package com.polymarket.markets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A group of related markets as discovery published it. */
public record DiscoveredEvent(
        @NonNull String id,
        @NonNull Optional<String> ticker,
        Optional<String> slug,
        Optional<String> title,
        Optional<Instant> startsAt,
        Optional<Instant> endsAt,
        Optional<Boolean> negRisk,
        List<DiscoveredMarket> markets) {

    public DiscoveredEvent {
        markets = List.copyOf(markets);
    }
}
