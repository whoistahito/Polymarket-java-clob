package com.polymarket.markets;

import java.util.List;
import java.util.Objects;
import lombok.NonNull;

/** What a public search matched. Each side is empty rather than absent when nothing matched. */
public record SearchResults(@NonNull List<DiscoveredEvent> events, @NonNull List<MarketTag> tags) {

    public SearchResults {
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
        tags = List.copyOf(Objects.requireNonNullElse(tags, List.of()));
    }
}
