package com.polymarket.streaming;

import java.math.BigDecimal;
import lombok.NonNull;

/** Binance reference price update ({@code topic: "crypto_prices"}). {@code symbol} is lowercase, e.g. {@code "btcusdt"}. */
public record BinancePriceEvent(@NonNull String symbol, long timestamp, @NonNull BigDecimal value) {
}
