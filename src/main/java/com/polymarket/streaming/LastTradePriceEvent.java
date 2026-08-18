package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.Optional;

/** Last-trade-price update ({@code event_type: "last_trade_price"}), emitted after each execution. */
public record LastTradePriceEvent(
        String assetId,
        String market,
        BigDecimal price,
        Optional<String> side,
        Optional<BigDecimal> size,
        Optional<String> feeRateBps,
        String timestamp,
        Optional<String> transactionHash) {

    public LastTradePriceEvent {
        side = side == null ? Optional.empty() : side;
        size = size == null ? Optional.empty() : size;
        feeRateBps = feeRateBps == null ? Optional.empty() : feeRateBps;
        transactionHash = transactionHash == null ? Optional.empty() : transactionHash;
    }
}
