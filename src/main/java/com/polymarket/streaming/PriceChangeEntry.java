package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** One entry within a {@link PriceChangeEvent} batch. */
public record PriceChangeEntry(@NonNull String assetId, @NonNull BigDecimal price,
        @NonNull Optional<BigDecimal> size, @NonNull String side, @NonNull Optional<String> hash,
        @NonNull Optional<BigDecimal> bestBid, @NonNull Optional<BigDecimal> bestAsk) {
}
