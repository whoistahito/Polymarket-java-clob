package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One settled fill. Amounts are exact; an absent field was never sent. */
public record TradeRecord(
        Optional<String> proxyWallet,
        TradedSide side,
        String asset,
        String conditionId,
        BigDecimal size,
        BigDecimal price,
        Instant executedAt,
        Optional<String> transactionHash,
        MarketReference market) {

    public TradeRecord {
        Objects.requireNonNull(proxyWallet, "proxyWallet");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(executedAt, "executedAt");
        Objects.requireNonNull(transactionHash, "transactionHash");
        Objects.requireNonNull(market, "market");
    }
}
