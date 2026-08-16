package com.polymarket.markets;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One semantic market, whichever discovery response carried it. Absent wire values stay
 * absent; nothing here is an executable trading rule.
 */
public record DiscoveredMarket(
        String id,
        Optional<String> conditionId,
        Optional<String> slug,
        Optional<String> question,
        List<MarketOutcome> outcomes,
        MarketState state,
        Optional<Instant> startsAt,
        Optional<Instant> endsAt,
        MarketPricing pricing,
        MarketMetadata metadata) {

    public DiscoveredMarket {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(pricing, "pricing");
        Objects.requireNonNull(metadata, "metadata");
        outcomes = List.copyOf(outcomes);
    }
}
