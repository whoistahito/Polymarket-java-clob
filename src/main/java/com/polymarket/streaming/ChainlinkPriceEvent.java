package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/** Chainlink oracle price update ({@code topic: "crypto_prices_chainlink"}). {@code symbol} is slash-separated, e.g. {@code "btc/usd"}. */
public record ChainlinkPriceEvent(@NonNull String symbol, long timestamp, @NonNull BigDecimal value
        ) {
}
