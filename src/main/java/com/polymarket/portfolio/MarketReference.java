package com.polymarket.portfolio;

import java.util.Optional;
import lombok.NonNull;

/** Human-facing market labels carried by a portfolio row. Absent is not empty text. */
public record MarketReference(
        @NonNull Optional<String> title,
        @NonNull Optional<String> slug,
        @NonNull Optional<String> eventSlug,
        @NonNull Optional<String> outcome,
        @NonNull Optional<Integer> outcomeIndex) {
}
