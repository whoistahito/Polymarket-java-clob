package com.polymarket.strategy.scalp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.strategy.scalp.BookSnapshot.TokenQuote;
import com.polymarket.strategy.scalp.ScalpDecision.Action;
import com.polymarket.strategy.scalp.ScalpState.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScalpStrategyTest {

  private static final ScalpConfig CFG =
      new ScalpConfig(0.45, 0.05, 60_000, 50, 180_000);
  private static final String UP = "up-token";
  private static final String DOWN = "down-token";
  private static final long WINDOW = 1_000_000L;

  // Entry-lead window is [WINDOW - entryLeadMs, WINDOW) = [820_000, 1_000_000).
  private static final long WITHIN_LEAD = 900_000L;
  private static final long TOO_EARLY = 500_000L;

  private static TokenQuote q(String id, double bid, double ask) {
    return new TokenQuote(id, bid, ask, 100);
  }

  private static TokenQuote noAsk(String id, double bid) {
    return new TokenQuote(id, bid, BookSnapshot.NO_ASK, 0);
  }

  private static TokenQuote noBid(String id, double ask) {
    return new TokenQuote(id, 0.0, ask, 100);
  }

  // ---- SEEKING ----

  @Test
  @DisplayName("SEEKING picks the cheaper eligible side and rests entry buys")
  void picksCheaperSide() {
    BookSnapshot book = new BookSnapshot(q(UP, 0.30, 0.33), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(), book, 0, WITHIN_LEAD, WINDOW);

    assertEquals(Action.PLACE_ENTRY, r.decision().action());
    assertEquals(UP, r.decision().tokenId());
    assertEquals(0.33, r.decision().price());
    assertEquals(50, r.decision().sizeUsdc());
    assertEquals(Phase.ACCUMULATING, r.nextState().phase());
    assertEquals(0.38, r.nextState().targetPx(), 1e-9); // 0.33 + 0.05
  }

  @Test
  @DisplayName("SEEKING skips when both sides are above maxEntryPrice")
  void skipsWhenNothingCheap() {
    BookSnapshot book = new BookSnapshot(q(UP, 0.48, 0.50), q(DOWN, 0.46, 0.52));
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(), book, 0, WITHIN_LEAD, WINDOW);

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.SEEKING, r.nextState().phase());
  }

  @Test
  @DisplayName("SEEKING ignores a side with no ask")
  void ignoresSideWithNoAsk() {
    BookSnapshot book = new BookSnapshot(noAsk(UP, 0.30), q(DOWN, 0.40, 0.44));
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(), book, 0, WITHIN_LEAD, WINDOW);

    assertEquals(Action.PLACE_ENTRY, r.decision().action());
    assertEquals(DOWN, r.decision().tokenId());
  }

  @Test
  @DisplayName("SEEKING abandons the market once the window is already open")
  void abandonsAfterWindowOpen() {
    BookSnapshot book = new BookSnapshot(q(UP, 0.30, 0.33), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(), book, 0, WINDOW, WINDOW);

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.DONE, r.nextState().phase());
    assertNull(r.nextState().cheapTokenId()); // never entered; nothing to protect
  }

  @Test
  @DisplayName("SEEKING stays quiet before the entry-lead window opens, even with a cheap side available")
  void staysQuietTooEarly() {
    BookSnapshot book = new BookSnapshot(q(UP, 0.30, 0.33), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(), book, 0, TOO_EARLY, WINDOW);

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.SEEKING, r.nextState().phase());
  }

  @Test
  @DisplayName("SEEKING enters right at the entry-lead boundary")
  void entersAtLeadBoundary() {
    long boundary = WINDOW - CFG.entryLeadMs(); // 820_000
    BookSnapshot book = new BookSnapshot(q(UP, 0.30, 0.33), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(), book, 0, boundary, WINDOW);

    assertEquals(Action.PLACE_ENTRY, r.decision().action());
  }

  @Test
  @DisplayName("target price is capped at MAX_TARGET_PRICE")
  void targetCapped() {
    ScalpConfig fat = new ScalpConfig(0.45, 0.60, 60_000, 50, 180_000);
    BookSnapshot book = new BookSnapshot(q(UP, 0.40, 0.44), q(DOWN, 0.60, 0.70));
    var r = ScalpStrategy.decide(fat, ScalpState.initial(), book, 0, WITHIN_LEAD, WINDOW);

    assertEquals(ScalpConfig.MAX_TARGET_PRICE, r.nextState().targetPx(), 1e-9); // 0.44+0.60 capped
  }

  // ---- ACCUMULATING ----
  // entryPx=0.25 -> expectedShares = orderSizeUsdc/entryPx = 50/0.25 = 200 (a clean full-fill size).

  @Test
  @DisplayName("ACCUMULATING keeps waiting on a partial fill, well short of the full order size")
  void waitsForFullFill() {
    ScalpState acc = ScalpState.accumulating(UP, 0.25, 0.30, 0);
    BookSnapshot book = new BookSnapshot(q(UP, 0.24, 0.26), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, acc, book, 50, 10_000, WINDOW); // 50 of 200 shares

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.ACCUMULATING, r.nextState().phase());
  }

  @Test
  @DisplayName("ACCUMULATING rests a take-profit sell only once the entry is fully filled")
  void restsExitOnFullFill() {
    ScalpState acc = ScalpState.accumulating(UP, 0.25, 0.30, 0);
    BookSnapshot book = new BookSnapshot(q(UP, 0.27, 0.29), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, acc, book, 200, 50_000, WINDOW); // full 200-share fill

    assertEquals(Action.PLACE_EXIT, r.decision().action());
    assertEquals(UP, r.decision().tokenId());
    assertEquals(0.30, r.decision().price());
    assertEquals(200, r.decision().sizeShares());
    assertEquals(Phase.EXITING, r.nextState().phase());
  }

  @Test
  @DisplayName("ACCUMULATING treats a fill within float/tick rounding tolerance as fully filled")
  void nearFullFillWithinToleranceTriggersExit() {
    ScalpState acc = ScalpState.accumulating(UP, 0.25, 0.30, 0);
    BookSnapshot book = new BookSnapshot(q(UP, 0.27, 0.29), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, acc, book, 199.995, 50_000, WINDOW); // 0.005 short of 200

    assertEquals(Action.PLACE_EXIT, r.decision().action());
  }

  @Test
  @DisplayName("EXITING's clock starts at the fill time, not the original order-placement time")
  void exitClockStartsAtFillTime() {
    ScalpState acc = ScalpState.accumulating(UP, 0.25, 0.30, 0); // order placed at t=0
    BookSnapshot book = new BookSnapshot(q(UP, 0.27, 0.29), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, acc, book, 200, 50_000, WINDOW); // fills 50s later

    assertEquals(50_000L, r.nextState().entryTimeMs());
  }

  @Test
  @DisplayName("ACCUMULATING cancels and finishes if window opens with no fill, retaining the token for late-fill safety")
  void cancelsIfWindowOpensUnfilled() {
    ScalpState acc = ScalpState.accumulating(UP, 0.33, 0.38, 0);
    BookSnapshot book = new BookSnapshot(q(UP, 0.32, 0.34), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, acc, book, 0, WINDOW, WINDOW);

    assertEquals(Action.CANCEL_AND_DONE, r.decision().action());
    assertEquals(Phase.DONE, r.nextState().phase());
    assertEquals(UP, r.nextState().cheapTokenId());
  }

  @Test
  @DisplayName("ACCUMULATING flattens a partial fill at the bid if window opens")
  void flattensPartialAtWindowOpen() {
    ScalpState acc = ScalpState.accumulating(UP, 0.33, 0.38, 0);
    BookSnapshot book = new BookSnapshot(q(UP, 0.36, 0.39), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, acc, book, 40, WINDOW, WINDOW);

    assertEquals(Action.MARKET_FLATTEN, r.decision().action());
    assertEquals(UP, r.decision().tokenId());
    assertEquals(0.36, r.decision().price()); // sell at current bid
    assertEquals(40, r.decision().sizeShares());
    assertEquals(Phase.DONE, r.nextState().phase());
    assertEquals(UP, r.nextState().cheapTokenId());
  }

  // ---- EXITING ----

  @Test
  @DisplayName("EXITING finishes flat when the take-profit fills")
  void doneWhenExitFilled() {
    ScalpState ex = ScalpState.accumulating(UP, 0.33, 0.38, 0).exiting(0);
    BookSnapshot book = new BookSnapshot(q(UP, 0.38, 0.40), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ex, book, 0, 20_000, WINDOW);

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.DONE, r.nextState().phase());
    assertEquals(UP, r.nextState().cheapTokenId());
  }

  @Test
  @DisplayName("EXITING holds while target is unfilled and within time/window limits")
  void holdsExit() {
    ScalpState ex = ScalpState.accumulating(UP, 0.33, 0.38, 0).exiting(1_000); // filled at t=1000
    BookSnapshot book = new BookSnapshot(q(UP, 0.35, 0.37), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ex, book, 200, 30_000, WINDOW); // 29s since fill < 60s time-stop

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.EXITING, r.nextState().phase());
  }

  @Test
  @DisplayName("EXITING flattens at the bid once the time-stop elapses, measured from the fill")
  void flattensOnTimeStop() {
    ScalpState ex = ScalpState.accumulating(UP, 0.33, 0.38, 0).exiting(1_000); // filled at t=1000
    BookSnapshot book = new BookSnapshot(q(UP, 0.34, 0.37), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ex, book, 200, 61_001, WINDOW); // 60.001s since fill >= 60s

    assertEquals(Action.MARKET_FLATTEN, r.decision().action());
    assertEquals(0.34, r.decision().price());
    assertEquals(200, r.decision().sizeShares());
    assertEquals(Phase.DONE, r.nextState().phase());
  }

  @Test
  @DisplayName("EXITING flattens at the bid if window opens before the time-stop")
  void flattensAtWindowOpen() {
    ScalpState ex = ScalpState.accumulating(UP, 0.33, 0.38, 0).exiting(WINDOW - 5_000);
    BookSnapshot book = new BookSnapshot(q(UP, 0.36, 0.39), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ex, book, 200, WINDOW, WINDOW);

    assertEquals(Action.MARKET_FLATTEN, r.decision().action());
    assertEquals(Phase.DONE, r.nextState().phase());
  }

  @Test
  @DisplayName("EXITING retries instead of selling at price 0 when there's no bid")
  void exitingRetriesWhenNoBid() {
    ScalpState ex = ScalpState.accumulating(UP, 0.33, 0.38, 0).exiting(0);
    BookSnapshot book = new BookSnapshot(noBid(UP, 0.37), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ex, book, 200, 61_001, WINDOW); // time-stopped, but no bid

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.EXITING, r.nextState().phase()); // stays put, retries next tick
  }

  // ---- DONE ----

  @Test
  @DisplayName("DONE is terminal once actually flat")
  void doneIsTerminalWhenFlat() {
    BookSnapshot book = new BookSnapshot(q(UP, 0.30, 0.33), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.done(null), book, 0, 0, WINDOW);

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.DONE, r.nextState().phase());
  }

  @Test
  @DisplayName("DONE re-flattens a late fill that lands after we thought we were flat")
  void doneCorrectivelyFlattensLateFill() {
    BookSnapshot book = new BookSnapshot(q(UP, 0.34, 0.36), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.done(UP), book, 15, 500_000, WINDOW);

    assertEquals(Action.MARKET_FLATTEN, r.decision().action());
    assertEquals(UP, r.decision().tokenId());
    assertEquals(0.34, r.decision().price());
    assertEquals(15, r.decision().sizeShares());
    assertEquals(Phase.DONE, r.nextState().phase());
    assertEquals(UP, r.nextState().cheapTokenId()); // kept in case another late fill follows
  }

  @Test
  @DisplayName("DONE retries the late-fill flatten instead of selling at price 0 when there's no bid")
  void doneCorrectiveFlattenRetriesWhenNoBid() {
    BookSnapshot book = new BookSnapshot(noBid(UP, 0.36), q(DOWN, 0.60, 0.66));
    var r = ScalpStrategy.decide(CFG, ScalpState.done(UP), book, 15, 500_000, WINDOW);

    assertEquals(Action.NONE, r.decision().action());
    assertEquals(Phase.DONE, r.nextState().phase());
    assertEquals(UP, r.nextState().cheapTokenId());
  }

  // ---- lifecycle ----

  @Test
  @DisplayName("happy path: seek -> full fill -> exit at target -> done, flat throughout")
  void happyPathLifecycle() {
    var r = ScalpStrategy.decide(CFG, ScalpState.initial(),
        new BookSnapshot(q(UP, 0.24, 0.25), q(DOWN, 0.60, 0.66)), 0, WITHIN_LEAD, WINDOW);
    assertEquals(Action.PLACE_ENTRY, r.decision().action());

    r = ScalpStrategy.decide(CFG, r.nextState(),
        new BookSnapshot(q(UP, 0.27, 0.29), q(DOWN, 0.60, 0.66)), 200, 905_000, WINDOW);
    assertEquals(Action.PLACE_EXIT, r.decision().action());
    assertEquals(905_000L, r.nextState().entryTimeMs());

    r = ScalpStrategy.decide(CFG, r.nextState(),
        new BookSnapshot(q(UP, 0.30, 0.32), q(DOWN, 0.60, 0.66)), 0, 910_000, WINDOW);
    assertEquals(Phase.DONE, r.nextState().phase());
    assertTrue(r.decision().action() == Action.NONE);
  }
}
