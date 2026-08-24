package com.polymarket.markets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * One semantic market, whichever discovery response carried it. Absent wire values stay
 * absent; nothing here is an executable trading rule.
 */
public record DiscoveredMarket(
        @NonNull String id,
        @NonNull Optional<String> conditionId,
        Optional<String> slug,
        Optional<String> question,
        List<MarketOutcome> outcomes,
        @NonNull MarketState state,
        Optional<Instant> startsAt,
        Optional<Instant> endsAt,
        @NonNull MarketPricing pricing,
        @NonNull MarketMetadata metadata) {

    public DiscoveredMarket {
        outcomes = List.copyOf(outcomes);
    }
}
