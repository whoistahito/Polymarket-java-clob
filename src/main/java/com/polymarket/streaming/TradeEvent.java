package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** User trade-execution message ({@code event_type: "trade"}), authenticated-channel only. */
public record TradeEvent(
        String id,
        String market,
        String assetId,
        String side,
        BigDecimal size,
        BigDecimal price,
        String status,
        String type,
        Optional<String> lastUpdate,
        Optional<String> matchTime,
        Optional<String> timestamp,
        Optional<String> outcome,
        Optional<String> owner,
        Optional<String> tradeOwner,
        Optional<String> makerAddress,
        Optional<String> takerOrderId,
        List<MakerOrder> makerOrders,
        Optional<String> feeRateBps,
        Optional<String> transactionHash,
        Optional<Integer> bucketIndex,
        Optional<String> traderSide) {

    public TradeEvent {
        lastUpdate = lastUpdate == null ? Optional.empty() : lastUpdate;
        matchTime = matchTime == null ? Optional.empty() : matchTime;
        timestamp = timestamp == null ? Optional.empty() : timestamp;
        outcome = outcome == null ? Optional.empty() : outcome;
        owner = owner == null ? Optional.empty() : owner;
        tradeOwner = tradeOwner == null ? Optional.empty() : tradeOwner;
        makerAddress = makerAddress == null ? Optional.empty() : makerAddress;
        takerOrderId = takerOrderId == null ? Optional.empty() : takerOrderId;
        makerOrders = makerOrders == null ? List.of() : List.copyOf(makerOrders);
        feeRateBps = feeRateBps == null ? Optional.empty() : feeRateBps;
        transactionHash = transactionHash == null ? Optional.empty() : transactionHash;
        bucketIndex = bucketIndex == null ? Optional.empty() : bucketIndex;
        traderSide = traderSide == null ? Optional.empty() : traderSide;
    }
}
