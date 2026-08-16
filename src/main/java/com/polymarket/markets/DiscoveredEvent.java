package com.polymarket.markets;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A group of related markets as discovery published it. */
public record DiscoveredEvent(
        String id,
        Optional<String> ticker,
        Optional<String> slug,
        Optional<String> title,
        Optional<Instant> startsAt,
        Optional<Instant> endsAt,
        Optional<Boolean> negRisk,
        List<DiscoveredMarket> markets) {

    public DiscoveredEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(negRisk, "negRisk");
        markets = List.copyOf(markets);
    }
}
