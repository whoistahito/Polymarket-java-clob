package com.polymarket.markets;

import java.util.List;
import java.util.Objects;

/** What a public search matched. Each side is empty rather than absent when nothing matched. */
public record SearchResults(List<DiscoveredEvent> events, List<MarketTag> tags) {

    public SearchResults {
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
        tags = List.copyOf(Objects.requireNonNullElse(tags, List.of()));
    }
}
