package com.polymarket.portfolio;

import java.util.Objects;
import java.util.Optional;

/** Human-facing market labels carried by a portfolio row. Absent is not empty text. */
public record MarketReference(
        Optional<String> title,
        Optional<String> slug,
        Optional<String> eventSlug,
        Optional<String> outcome,
        Optional<Integer> outcomeIndex) {

    public MarketReference {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(eventSlug, "eventSlug");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(outcomeIndex, "outcomeIndex");
    }
}
