package com.polymarket.streaming;

import java.math.BigDecimal;

/** Chainlink oracle price update ({@code topic: "crypto_prices_chainlink"}). {@code symbol} is slash-separated, e.g. {@code "btc/usd"}. */
public record ChainlinkPriceEvent(String symbol, long timestamp, BigDecimal value) {
}
