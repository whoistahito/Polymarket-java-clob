package com.polymarket.strategy.scalp;

/**
 * Immutable per-market position in the scalp lifecycle. Advanced only by {@link ScalpStrategy}.
 *
 * <ul>
 *   <li>SEEKING — no position yet; hunting for a mispriced cheap side to enter.
 *   <li>ACCUMULATING — entry buys resting; waiting for the entry to fully fill before the window
 *       opens.
 *   <li>EXITING — holding shares with a take-profit sell resting.
 *   <li>DONE — flat and finished (target hit, flattened, or missed the window). {@code
 *       cheapTokenId} is kept (not nulled) so a late fill arriving after this transition can still
 *       be identified and flattened by {@link ScalpStrategy}.
 * </ul>
 */
public record ScalpState(
    Phase phase, String cheapTokenId, double entryPx, double targetPx, long entryTimeMs) {

  public enum Phase {
    SEEKING,
    ACCUMULATING,
    EXITING,
    DONE
  }

  public static ScalpState initial() {
    return new ScalpState(Phase.SEEKING, null, 0, 0, 0);
  }

  static ScalpState accumulating(String cheapTokenId, double entryPx, double targetPx, long nowMs) {
    return new ScalpState(Phase.ACCUMULATING, cheapTokenId, entryPx, targetPx, nowMs);
  }

  /**
   * Enters EXITING with the clock reset to {@code fillTimeMs} (when the entry actually filled),
   * not when the order was originally placed — the time-stop measures how long we've held the
   * position, not how long the entry sat resting.
   */
  ScalpState exiting(long fillTimeMs) {
    return new ScalpState(Phase.EXITING, cheapTokenId, entryPx, targetPx, fillTimeMs);
  }

  static ScalpState done(String cheapTokenId) {
    return new ScalpState(Phase.DONE, cheapTokenId, 0, 0, 0);
  }
}
