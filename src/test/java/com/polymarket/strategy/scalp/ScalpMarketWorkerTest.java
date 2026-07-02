package com.polymarket.strategy.scalp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.OrderBookLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Wiring test: book tick -> strategy -> executor dispatch. Domain correctness is covered elsewhere. */
class ScalpMarketWorkerTest {

  private static final String UP = "up-token";
  private static final String DOWN = "down-token";
  private static final ScalpConfig CFG = new ScalpConfig(0.45, 0.05, 60_000, 50, 180_000);

  /** Records every executor call so tests can assert what was dispatched. */
  private static final class RecordingExecutor implements ScalpExecutor {
    record Call(String action, String tokenId, double price, double size) {}

    final List<Call> calls = new ArrayList<>();

    public void placeEntry(String m, String t, double p, double s) {
      calls.add(new Call("entry", t, p, s));
    }

    public void placeExit(String m, String t, double p, double s) {
      calls.add(new Call("exit", t, p, s));
    }

    public void marketFlatten(String m, String t, double p, double s) {
      calls.add(new Call("flatten", t, p, s));
    }

    public void cancelAll(String m, String t) {
      calls.add(new Call("cancel", t, 0, 0));
    }

    Call last() {
      return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }
  }

  // 60s out comfortably satisfies the 180s entry-lead window at construction time.
  private static long windowIn(long millisFromNow) {
    return System.currentTimeMillis() + millisFromNow;
  }

  private static BookUpdate book(String tokenId, String bid, String ask) {
    BookUpdate u = new BookUpdate();
    u.setAssetId(tokenId);
    u.setBids(List.of(level(bid, "500")));
    u.setAsks(List.of(level(ask, "500")));
    return u;
  }

  private static OrderBookLevel level(String price, String size) {
    OrderBookLevel l = new OrderBookLevel();
    l.setPrice(price);
    l.setSize(size);
    return l;
  }

  @Test
  void tickWithCheapSideDispatchesEntry() {
    RecordingExecutor exec = new RecordingExecutor();
    ScalpMarketWorker worker = new ScalpMarketWorker("mkt", UP, DOWN, CFG, windowIn(60_000), exec);

    worker.onMessage(book(DOWN, "0.60", "0.66"));
    worker.onMessage(book(UP, "0.30", "0.33"));

    assertEquals("entry", exec.last().action());
    assertEquals(UP, exec.last().tokenId());
    assertEquals(0.33, exec.last().price());
    assertEquals(50, exec.last().size());
    assertEquals(ScalpState.Phase.ACCUMULATING, worker.state().phase());
  }

  @Test
  void fullFillThenTargetRunsExitThenDone() {
    RecordingExecutor exec = new RecordingExecutor();
    ScalpMarketWorker worker = new ScalpMarketWorker("mkt", UP, DOWN, CFG, windowIn(60_000), exec);
    worker.onMessage(book(DOWN, "0.60", "0.66"));
    worker.onMessage(book(UP, "0.24", "0.25")); // entryPx=0.25 -> expectedShares = 50/0.25 = 200

    worker.updatePosition(UP, 80); // partial fill: NOT yet a full entry -> no exit
    assertEquals("entry", exec.last().action());
    assertEquals(ScalpState.Phase.ACCUMULATING, worker.state().phase());

    worker.updatePosition(UP, 200); // fully filled -> rest exit
    assertEquals("exit", exec.last().action());
    assertEquals(0.30, exec.last().price());
    assertEquals(200, exec.last().size());
    assertEquals(ScalpState.Phase.EXITING, worker.state().phase());

    worker.updatePosition(UP, 0); // target filled -> DONE, flat
    assertEquals(ScalpState.Phase.DONE, worker.state().phase());
  }

  @Test
  void lateFillAfterDoneIsCorrectivelyFlattened() {
    RecordingExecutor exec = new RecordingExecutor();
    ScalpMarketWorker worker = new ScalpMarketWorker("mkt", UP, DOWN, CFG, windowIn(60_000), exec);
    worker.onMessage(book(DOWN, "0.60", "0.66"));
    worker.onMessage(book(UP, "0.24", "0.25"));
    worker.updatePosition(UP, 200); // -> EXITING
    worker.updatePosition(UP, 0); // exit confirmed -> DONE, flat

    // A stray fill lands after DONE (cancel/fill race on the exchange).
    worker.onMessage(book(UP, "0.31", "0.33"));
    worker.updatePosition(UP, 15);

    assertEquals("flatten", exec.last().action());
    assertEquals(UP, exec.last().tokenId());
    assertEquals(0.31, exec.last().price());
    assertEquals(15, exec.last().size());
    assertEquals(ScalpState.Phase.DONE, worker.state().phase());
  }

  @Test
  void positionUpdatesForOtherTokenAreIgnored() {
    RecordingExecutor exec = new RecordingExecutor();
    ScalpMarketWorker worker = new ScalpMarketWorker("mkt", UP, DOWN, CFG, windowIn(60_000), exec);
    worker.onMessage(book(DOWN, "0.60", "0.66"));
    worker.onMessage(book(UP, "0.24", "0.25")); // entered UP
    int callsBefore = exec.calls.size();

    worker.updatePosition(DOWN, 500); // not the token we hold -> ignored
    assertEquals(callsBefore, exec.calls.size());
    assertEquals(0, worker.positionShares());
  }

  @Test
  void positionUpdateBeforeEntryIsIgnored() {
    RecordingExecutor exec = new RecordingExecutor();
    ScalpMarketWorker worker = new ScalpMarketWorker("mkt", UP, DOWN, CFG, windowIn(60_000), exec);
    // No entry yet: cheapTokenId is null.
    worker.updatePosition(UP, 100);
    assertEquals(0, worker.positionShares());
    assertEquals(ScalpState.Phase.SEEKING, worker.state().phase());
  }
}
