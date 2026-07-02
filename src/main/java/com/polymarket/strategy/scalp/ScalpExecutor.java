package com.polymarket.strategy.scalp;

/**
 * Side-effecting boundary for the scalp: turns {@link ScalpDecision}s into real orders. Kept as an
 * interface so the pure {@link ScalpMarketWorker} loop can be driven by a fake in tests. The live
 * implementation posts via {@link com.polymarket.client.AsyncPolymarketClient}.
 *
 * <p>{@link ScalpMarketWorker} invokes these methods while holding its internal lock, so
 * implementations must not block the calling thread — kick off network I/O asynchronously (e.g.
 * {@code AsyncPolymarketClient} returns a {@code CompletableFuture} immediately, matching the
 * pattern already used by {@code ExecutionEngine}) rather than waiting on the result.
 *
 * <p>{@link #marketFlatten} and {@link #cancelAll} should also cancel any still-resting entry buy
 * for the token as a best-effort measure to minimize residual exposure. This is not the only
 * safety net, though: {@link ScalpStrategy} independently re-checks the position every tick even
 * after DONE and will re-flatten any late fill that slips through, so a race between the cancel and
 * a fill is still caught.
 */
public interface ScalpExecutor {

  /** Rest passive limit buys on the cheap side, working up to {@code sizeUsdc} notional. */
  void placeEntry(String marketId, String tokenId, double price, double sizeUsdc);

  /** Rest a take-profit limit sell for the held shares. */
  void placeExit(String marketId, String tokenId, double price, double sizeShares);

  /** Cut the remaining position at the current bid. */
  void marketFlatten(String marketId, String tokenId, double bidPrice, double sizeShares);

  /** Cancel any resting entry buys for this market; nothing filled before the window. */
  void cancelAll(String marketId, String tokenId);
}
