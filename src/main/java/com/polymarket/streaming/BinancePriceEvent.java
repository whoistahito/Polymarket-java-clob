package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/**
 * Binance reference price update ({@code topic: "crypto_prices"}). {@code symbol} is lowercase,
 * e.g. {@code "btcusdt"}. {@code observedAt} is the RTDS envelope time — when the stream saw the
 * update — and {@code timestamp} is when the source produced the price; they are not the same fact.
 */
public record BinancePriceEvent(@NonNull String symbol, long observedAt, long timestamp,
        @NonNull BigDecimal value) {
}
