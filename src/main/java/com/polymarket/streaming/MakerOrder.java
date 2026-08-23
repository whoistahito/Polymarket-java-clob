package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.Optional;

/** Maker-order details nested inside a {@link TradeEvent}. */
public record MakerOrder(
        String assetId,
        Optional<BigDecimal> matchedAmount,
        String orderId,
        Optional<String> outcome,
        Optional<Integer> outcomeIndex,
        String side,
        Optional<String> owner,
        Optional<String> makerAddress,
        BigDecimal price,
        Optional<String> feeRateBps) {

    public MakerOrder {
        matchedAmount = matchedAmount == null ? Optional.empty() : matchedAmount;
        outcome = outcome == null ? Optional.empty() : outcome;
        outcomeIndex = outcomeIndex == null ? Optional.empty() : outcomeIndex;
        owner = owner == null ? Optional.empty() : owner;
        makerAddress = makerAddress == null ? Optional.empty() : makerAddress;
        feeRateBps = feeRateBps == null ? Optional.empty() : feeRateBps;
    }
}
