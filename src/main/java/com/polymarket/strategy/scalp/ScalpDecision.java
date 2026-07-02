package com.polymarket.strategy.scalp;

/**
 * What the adapter should do this tick. A flat data command — the domain never touches the network.
 *
 * @param action the command kind
 * @param tokenId the token to act on (null for NONE/CANCEL_AND_DONE without a leg)
 * @param price limit/market price for the order (0 when not applicable)
 * @param sizeShares share count for exits/flattens (0 when not applicable)
 * @param sizeUsdc notional to work for the entry (0 when not applicable)
 *     <p>ponytail: one record with unused fields per variant instead of a sealed interface per
 *     Action. Only 5 fixed variants, all built through the factories below, so nothing outside this
 *     file can construct an inconsistent combination today. Split into a sealed interface if a new
 *     Action needs its own shape or callers start pattern-matching on fields directly.
 */
public record ScalpDecision(
    Action action, String tokenId, double price, double sizeShares, double sizeUsdc) {

  public enum Action {
    /** Do nothing this tick. */
    NONE,
    /** Rest passive limit buys on the cheap side (farms maker rebates). */
    PLACE_ENTRY,
    /** Rest a take-profit limit sell for the whole position. */
    PLACE_EXIT,
    /** Cut the remaining position at the current bid — time-stop or window open. */
    MARKET_FLATTEN,
    /** Cancel resting entry buys; nothing filled before the window. Flat and finished. */
    CANCEL_AND_DONE
  }

  static final ScalpDecision NONE = new ScalpDecision(Action.NONE, null, 0, 0, 0);
  static final ScalpDecision CANCEL_AND_DONE =
      new ScalpDecision(Action.CANCEL_AND_DONE, null, 0, 0, 0);

  static ScalpDecision entry(String tokenId, double price, double sizeUsdc) {
    return new ScalpDecision(Action.PLACE_ENTRY, tokenId, price, 0, sizeUsdc);
  }

  static ScalpDecision exit(String tokenId, double price, double sizeShares) {
    return new ScalpDecision(Action.PLACE_EXIT, tokenId, price, sizeShares, 0);
  }

  static ScalpDecision flatten(String tokenId, double bidPrice, double sizeShares) {
    return new ScalpDecision(Action.MARKET_FLATTEN, tokenId, bidPrice, sizeShares, 0);
  }
}
