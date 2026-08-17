package com.polymarket.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One trade record read back from {@code GET /data/trades?id=}, keyed by trade ID. */
public record SettledTrade(
        String id,
        TradeStatus status,
        Side side,
        String assetId,
        BigDecimal size,
        BigDecimal price,
        Optional<Instant> matchTime,
        Optional<String> transactionHash) {

    public SettledTrade {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(matchTime, "matchTime");
        Objects.requireNonNull(transactionHash, "transactionHash");
    }
}
