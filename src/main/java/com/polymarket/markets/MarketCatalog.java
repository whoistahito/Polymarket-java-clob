package com.polymarket.markets;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Port for canonical market discovery. The domain declares it; an internal adapter
 * implements it, so no transport type reaches this package.
 */
public interface MarketCatalog {

    List<DiscoveredEvent> events(EventQuery query) throws IOException;

    Optional<DiscoveredEvent> eventBySlug(String slug) throws IOException;

    List<DiscoveredMarket> markets(MarketQuery query) throws IOException;

    Optional<DiscoveredMarket> market(String id) throws IOException;

    List<MarketTag> tags(int limit) throws IOException;

    List<MarketSeries> series(int limit) throws IOException;

    List<Sport> sports() throws IOException;

    SearchResults search(String query) throws IOException;
}
