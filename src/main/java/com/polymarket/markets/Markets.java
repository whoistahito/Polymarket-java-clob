package com.polymarket.markets;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Public market discovery. Every call is a credential-free read. */
public final class Markets {

    private final MarketCatalog catalog;

    public Markets(MarketCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public List<DiscoveredEvent> events(EventQuery query) throws IOException {
        return catalog.events(Objects.requireNonNull(query, "query"));
    }

    /** Empty when Gamma does not know the slug. */
    public Optional<DiscoveredEvent> eventBySlug(String slug) throws IOException {
        return catalog.eventBySlug(Objects.requireNonNull(slug, "slug"));
    }

    public List<DiscoveredMarket> markets(MarketQuery query) throws IOException {
        return catalog.markets(Objects.requireNonNull(query, "query"));
    }

    /** Empty when Gamma does not know the id. */
    public Optional<DiscoveredMarket> market(String id) throws IOException {
        return catalog.market(Objects.requireNonNull(id, "id"));
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
    public SearchResults search(String query) throws IOException {
        Objects.requireNonNull(query, "query");
        if (query.isBlank()) {
            throw new IllegalArgumentException("search query must not be blank");
        }
        return catalog.search(query);
    }
}
