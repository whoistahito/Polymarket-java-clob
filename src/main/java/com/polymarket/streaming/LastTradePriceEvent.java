package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** Last-trade-price update ({@code event_type: "last_trade_price"}), emitted after each execution. */
public record LastTradePriceEvent(@NonNull String assetId, @NonNull String market,
        @NonNull BigDecimal price, @NonNull Optional<String> side,
        @NonNull Optional<BigDecimal> size, @NonNull Optional<String> feeRateBps,
        @NonNull String timestamp, @NonNull Optional<String> transactionHash) {
}
