package com.polymarket.strategy.scalp;

import com.polymarket.strategy.scalp.BookSnapshot.TokenQuote;

/**
 * Pure decision core of the SHILIN-FLEET pre-window spread scalp. Given the current state, the book,
 * and the position, it returns the next command and the next state — no I/O, no clocks of its own.
 * All timing is passed in so it's fully deterministic and unit-testable.
 *
 * <p>Invariant: the strategy is market-neutral and MUST be flat by {@code windowOpenMs}. It never
 * bets on the up/down resolution; the edge is book convergence around the open plus maker rebates.
 * This invariant is enforced continuously, not just at transition time: {@code DONE} re-checks the
 * position every tick and re-flattens if a late fill (e.g. a cancel/fill race) leaves it non-zero.
 */
public final class ScalpStrategy {

  private ScalpStrategy() {}

  /** Tolerance for float/tick-size rounding when deciding an entry order has fully filled. */
  private static final double FULL_FILL_EPSILON_SHARES = 0.01;

  /** Command plus the state to carry into the next tick. */
  public record Result(ScalpDecision decision, ScalpState nextState) {}

  public static Result decide(
      ScalpConfig cfg,
      ScalpState state,
      BookSnapshot book,
      double positionShares,
      long nowMs,
      long windowOpenMs) {
    return switch (state.phase()) {
      case SEEKING -> seeking(cfg, state, book, nowMs, windowOpenMs);
      case ACCUMULATING -> accumulating(cfg, state, book, positionShares, nowMs, windowOpenMs);
      case EXITING -> exiting(cfg, state, book, positionShares, nowMs, windowOpenMs);
      case DONE -> done(state, book, positionShares);
    };
  }

  private static Result seeking(
      ScalpConfig cfg, ScalpState state, BookSnapshot book, long nowMs, long windowOpenMs) {
    // Once the window is open there's no time to enter and still exit flat — abandon this market.
    if (nowMs >= windowOpenMs) {
      return new Result(ScalpDecision.NONE, ScalpState.done(null));
    }
    // Too early: stay quiet until we're inside the configured entry-lead window.
    if (nowMs < windowOpenMs - cfg.entryLeadMs()) {
      return new Result(ScalpDecision.NONE, state);
    }

    TokenQuote cheap = cheaperEligibleSide(cfg, book);
    if (cheap == null) {
      return new Result(ScalpDecision.NONE, state); // nothing mispriced yet; keep watching
    }

    double entryPx = cheap.bestAsk();
    double targetPx = Math.min(entryPx + cfg.takeProfit(), ScalpConfig.MAX_TARGET_PRICE);
    ScalpState next = ScalpState.accumulating(cheap.tokenId(), entryPx, targetPx, nowMs);
    return new Result(ScalpDecision.entry(cheap.tokenId(), entryPx, cfg.orderSizeUsdc()), next);
  }

  private static Result accumulating(
      ScalpConfig cfg,
      ScalpState state,
      BookSnapshot book,
      double positionShares,
      long nowMs,
      long windowOpenMs) {
    if (nowMs >= windowOpenMs) {
      // Must be flat by the open: cut anything that filled, cancel the rest.
      return positionShares > 0
          ? flattenNow(state, book, positionShares)
          : new Result(ScalpDecision.CANCEL_AND_DONE, ScalpState.done(state.cheapTokenId()));
    }

    // Wait for the entry to FULLY fill (spec: wait_until(filled(cheap) OR window open)) before
    // resting the take-profit sell — an early "any fill" transition would leave the still-resting
    // remainder of the entry order to keep filling into an exit order sized for less than we hold.
    double expectedShares = cfg.orderSizeUsdc() / state.entryPx();
    if (positionShares >= expectedShares - FULL_FILL_EPSILON_SHARES) {
      ScalpState next = state.exiting(nowMs);
      return new Result(
          ScalpDecision.exit(state.cheapTokenId(), state.targetPx(), positionShares), next);
    }
    return new Result(ScalpDecision.NONE, state); // still waiting for a full fill
  }

  private static Result exiting(
      ScalpConfig cfg,
      ScalpState state,
      BookSnapshot book,
      double positionShares,
      long nowMs,
      long windowOpenMs) {
    if (positionShares <= 0) {
      return new Result(ScalpDecision.NONE, ScalpState.done(state.cheapTokenId())); // target hit, flat
    }
    boolean timeStopped = nowMs - state.entryTimeMs() >= cfg.timeStopMs();
    if (timeStopped || nowMs >= windowOpenMs) {
      return flattenNow(state, book, positionShares);
    }
    return new Result(ScalpDecision.NONE, state); // let the take-profit rest
  }

  /**
   * DONE is only a true no-op once actually flat. If a late fill (e.g. a cancel/fill race on the
   * exchange) leaves {@code positionShares} non-zero after we thought we were done, keep flattening
   * it every tick until confirmed flat.
   */
  private static Result done(ScalpState state, BookSnapshot book, double positionShares) {
    if (positionShares > 0 && state.cheapTokenId() != null) {
      return flattenNow(state, book, positionShares);
    }
    return new Result(ScalpDecision.NONE, state);
  }

  private static Result flattenNow(ScalpState state, BookSnapshot book, double positionShares) {
    double bid = book.quoteFor(state.cheapTokenId()).bestBid();
    if (bid <= 0) {
      // No bid to sell into right now — retry next tick instead of selling for free at price 0.
      return new Result(ScalpDecision.NONE, state);
    }
    return new Result(
        ScalpDecision.flatten(state.cheapTokenId(), bid, positionShares),
        ScalpState.done(state.cheapTokenId()));
  }

  /** The eligible side (ask &le; maxEntryPrice) with the lower ask, or null if neither qualifies. */
  private static TokenQuote cheaperEligibleSide(ScalpConfig cfg, BookSnapshot book) {
    TokenQuote best = null;
    for (TokenQuote q : new TokenQuote[] {book.a(), book.b()}) {
      if (q.hasAsk() && q.bestAsk() <= cfg.maxEntryPrice()) {
        if (best == null || q.bestAsk() < best.bestAsk()) {
          best = q;
        }
      }
    }
    return best;
  }
}
