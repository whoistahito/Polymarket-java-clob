package com.polymarket.strategy.scalp;

/**
 * Per-account tuning for the market-neutral pre-window spread scalp. These are the only values that
 * differ between wallets in the SHILIN-FLEET strategy.
 *
 * @param maxEntryPrice only ever buy the discounted side; skip if its ask exceeds this (e.g. 0.45)
 * @param takeProfit fixed price improvement targeted on exit, in probability points (e.g. 0.05)
 * @param timeStopMs flatten the position if still open after this long (e.g. 60_000)
 * @param orderSizeUsdc per-market notional to work (e.g. 50)
 * @param entryLeadMs start working this long before the measurement window opens (e.g. 180_000)
 */
public record ScalpConfig(
    double maxEntryPrice, double takeProfit, long timeStopMs, double orderSizeUsdc, long entryLeadMs) {

  /** Hard cap: never rest a take-profit sell above this, resolution risk isn't worth the last cent. */
  public static final double MAX_TARGET_PRICE = 0.98;

  public ScalpConfig {
    if (maxEntryPrice <= 0 || maxEntryPrice >= 1) {
      throw new IllegalArgumentException("maxEntryPrice must be in (0,1): " + maxEntryPrice);
    }
    if (takeProfit <= 0) {
      throw new IllegalArgumentException("takeProfit must be positive: " + takeProfit);
    }
    if (orderSizeUsdc <= 0) {
      throw new IllegalArgumentException("orderSizeUsdc must be positive: " + orderSizeUsdc);
    }
    if (timeStopMs <= 0) {
      throw new IllegalArgumentException("timeStopMs must be positive: " + timeStopMs);
    }
    if (entryLeadMs < 0) {
      throw new IllegalArgumentException("entryLeadMs must be non-negative: " + entryLeadMs);
    }
  }
}
