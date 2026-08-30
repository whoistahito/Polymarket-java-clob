package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/**
 * Chainlink oracle price update ({@code topic: "crypto_prices_chainlink"}). {@code symbol} is
 * slash-separated, e.g. {@code "btc/usd"}. {@code observedAt} is the RTDS envelope time;
 * {@code timestamp} is when the oracle produced the price.
 */
public record ChainlinkPriceEvent(@NonNull String symbol, long observedAt, long timestamp,
        @NonNull BigDecimal value) {
}
