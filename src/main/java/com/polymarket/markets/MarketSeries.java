package com.polymarket.markets;

import java.util.Objects;
import java.util.Optional;

/** A recurring family of events, such as a league season. */
public record MarketSeries(
        String id,
        Optional<String> ticker,
        Optional<String> slug,
        Optional<String> title,
        Optional<String> recurrence) {

    public MarketSeries {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(recurrence, "recurrence");
    }
}
