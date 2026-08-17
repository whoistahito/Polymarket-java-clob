package com.polymarket.builders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One settled trade attributed to the builder, exactly as the CLOB recorded it. */
public record BuilderTrade(
        String id,
        String tradeType,
        Optional<String> takerOrderHash,
        String builder,
        String market,
        String assetId,
        Side side,
        BigDecimal size,
        Optional<BigDecimal> sizeUsdc,
        BigDecimal price,
        String status,
        Optional<String> outcome,
        Optional<Integer> outcomeIndex,
        String owner,
        String maker,
        Optional<String> transactionHash,
        Optional<Instant> matchTime,
        Optional<BigDecimal> fee,
        Optional<BigDecimal> feeUsdc,
        Optional<String> errorMessage,
        Optional<Instant> createdAt,
        Optional<Instant> updatedAt) {

    public BuilderTrade {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tradeType, "tradeType");
        Objects.requireNonNull(takerOrderHash, "takerOrderHash");
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(sizeUsdc, "sizeUsdc");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(outcomeIndex, "outcomeIndex");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(maker, "maker");
        Objects.requireNonNull(transactionHash, "transactionHash");
        Objects.requireNonNull(matchTime, "matchTime");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(feeUsdc, "feeUsdc");
        Objects.requireNonNull(errorMessage, "errorMessage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
