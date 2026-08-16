package com.polymarket.markets;

import java.util.Objects;
import java.util.Optional;

/** A discovery tag. */
public record MarketTag(String id, Optional<String> label, Optional<String> slug) {

    public MarketTag {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(slug, "slug");
    }
}
