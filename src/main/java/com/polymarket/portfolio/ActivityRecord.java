package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One account-history row. Deposits and conversions are not market-scoped, so the market
 * fields are optional rather than blanked out.
 */
public record ActivityRecord(
        Optional<String> proxyWallet,
        ActivityKind kind,
        Instant occurredAt,
        Optional<String> conditionId,
        Optional<String> asset,
        Optional<BigDecimal> size,
        Optional<BigDecimal> usdcSize,
        Optional<BigDecimal> price,
        Optional<TradedSide> side,
        Optional<String> transactionHash,
        Optional<Boolean> combo,
        MarketReference market) {

    public ActivityRecord {
        Objects.requireNonNull(proxyWallet, "proxyWallet");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(usdcSize, "usdcSize");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(transactionHash, "transactionHash");
        Objects.requireNonNull(combo, "combo");
        Objects.requireNonNull(market, "market");
    }
}
