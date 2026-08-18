package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.Optional;

/** One entry within a {@link PriceChangeEvent} batch. */
public record PriceChangeEntry(
        String assetId,
        BigDecimal price,
        Optional<BigDecimal> size,
        String side,
        Optional<String> hash,
        Optional<BigDecimal> bestBid,
        Optional<BigDecimal> bestAsk) {

    public PriceChangeEntry {
        size = size == null ? Optional.empty() : size;
        hash = hash == null ? Optional.empty() : hash;
        bestBid = bestBid == null ? Optional.empty() : bestBid;
        bestAsk = bestAsk == null ? Optional.empty() : bestAsk;
    }
}
