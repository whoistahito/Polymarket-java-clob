package com.polymarket.streaming;

import java.math.BigDecimal;

/** Binance reference price update ({@code topic: "crypto_prices"}). {@code symbol} is lowercase, e.g. {@code "btcusdt"}. */
public record BinancePriceEvent(String symbol, long timestamp, BigDecimal value) {
}
