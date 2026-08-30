package com.polymarket.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/**
 * One trade record read back from {@code GET /data/trades}, keyed by trade ID. Every field the
 * wire omitted stays absent: a sparse record is reported, never completed with invented values.
 */
public record SettledTrade(
        @NonNull String id,
        @NonNull TradeStatus status,
        @NonNull Optional<Side> side,
        @NonNull Optional<String> assetId,
        @NonNull Optional<BigDecimal> size,
        @NonNull Optional<BigDecimal> price,
        @NonNull Optional<Instant> matchTime,
        @NonNull Optional<String> transactionHash,
        @NonNull Optional<String> errorMessage) {

    /** The settlement is over for this trade: it failed, or it confirmed with its hash on chain. */
    public boolean settled() {
        return status().is(TradeStatus.Known.FAILED)
                || (status().is(TradeStatus.Known.CONFIRMED) && transactionHash.isPresent());
    }
}
