package com.polymarket.rfq;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * A Combo-eligible market. Its YES and NO leg Position IDs come from the catalog, so no CTF
 * position id is ever computed locally.
 */
public record ComboMarket(
        @NonNull String id,
        @NonNull String conditionId,
        @NonNull ComboOutcome yes,
        @NonNull ComboOutcome no,
        @NonNull String slug,
        @NonNull String title,
        @NonNull Optional<String> imageUrl,
        @NonNull Optional<BigDecimal> volume,
        @NonNull List<String> tags) {

    public ComboMarket {
        tags = List.copyOf(tags);
    }
}
