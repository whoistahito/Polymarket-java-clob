package com.polymarket.markets;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Public market discovery. Every call is a credential-free read. */
public final class Markets {

    private final MarketCatalog catalog;

    public Markets(@NonNull MarketCatalog catalog) {
        this.catalog = catalog;
    }

    public List<DiscoveredEvent> events(@NonNull EventQuery query) throws IOException {
        return catalog.events(query);
    }

    /** Empty when Gamma does not know the slug. */
    public Optional<DiscoveredEvent> eventBySlug(@NonNull String slug) throws IOException {
        return catalog.eventBySlug(slug);
    }

    public List<DiscoveredMarket> markets(@NonNull MarketQuery query) throws IOException {
        return catalog.markets(query);
    }

    /** Empty when Gamma does not know the id. */
    public Optional<DiscoveredMarket> market(@NonNull String id) throws IOException {
        return catalog.market(id);
    }

    /** Bounded by {@code limit} so a reference read cannot become an unbounded page walk. */
    public List<MarketTag> tags(int limit) throws IOException {
        return catalog.tags(limit);
    }

    public List<MarketSeries> series(int limit) throws IOException {
        return catalog.series(limit);
    }

    public List<Sport> sports() throws IOException {
        return catalog.sports();
    }

    /** Matches events and tags; a blank query would return the whole catalogue, so reject it. */
    public SearchResults search(@NonNull String query) throws IOException {
        if (query.isBlank()) {
            throw new IllegalArgumentException("search query must not be blank");
        }
        return catalog.search(query);
    }
}
