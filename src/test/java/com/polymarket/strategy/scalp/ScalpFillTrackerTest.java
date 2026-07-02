package com.polymarket.strategy.scalp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.polymarket.ws.model.TradeMessage;
import org.junit.jupiter.api.Test;

class ScalpFillTrackerTest {

  private static final String TOKEN = "cheap-token";

  private static TradeMessage trade(String id, String side, String size, String status) {
    TradeMessage t = new TradeMessage();
    t.setId(id);
    t.setAssetId(TOKEN);
    t.setSide(side);
    t.setSize(size);
    t.setStatus(status);
    return t;
  }

  @Test
  void buysAccumulateNet() {
    ScalpFillTracker tracker = new ScalpFillTracker();
    assertEquals(50.0, tracker.apply(trade("t1", "BUY", "50", "MATCHED")));
    assertEquals(150.0, tracker.apply(trade("t2", "BUY", "100", "MATCHED")));
  }

  @Test
  void sellsReduceNet() {
    ScalpFillTracker tracker = new ScalpFillTracker();
    tracker.apply(trade("t1", "BUY", "200", "MATCHED"));
    assertEquals(150.0, tracker.apply(trade("t2", "SELL", "50", "MATCHED")));
    assertEquals(0.0, tracker.apply(trade("t3", "SELL", "150", "MATCHED")));
  }

  @Test
  void duplicateTradeIdCountedOnce() {
    ScalpFillTracker tracker = new ScalpFillTracker();
    assertEquals(200.0, tracker.apply(trade("t1", "BUY", "200", "MATCHED")));
    // Same fill re-delivered as it advances MATCHED -> MINED -> CONFIRMED.
    assertNull(tracker.apply(trade("t1", "BUY", "200", "MINED")));
    assertNull(tracker.apply(trade("t1", "BUY", "200", "CONFIRMED")));
  }

  @Test
  void nonFillStatusesIgnored() {
    ScalpFillTracker tracker = new ScalpFillTracker();
    assertNull(tracker.apply(trade("t1", "BUY", "200", "RETRYING")));
    assertNull(tracker.apply(trade("t2", "BUY", "200", "FAILED")));
    // A later real fill still lands.
    assertEquals(50.0, tracker.apply(trade("t3", "BUY", "50", "MATCHED")));
  }

  @Test
  void malformedTradeIgnored() {
    ScalpFillTracker tracker = new ScalpFillTracker();
    assertNull(tracker.apply(null));
    assertNull(tracker.apply(trade("t1", "BUY", "not-a-number", "MATCHED")));
    assertNull(tracker.apply(trade(null, "BUY", "50", "MATCHED")));
    // The bad-size trade id was not consumed, so a corrected re-send still applies.
    assertEquals(50.0, tracker.apply(trade("t1", "BUY", "50", "MATCHED")));
  }

  @Test
  void netNeverGoesNegative() {
    ScalpFillTracker tracker = new ScalpFillTracker();
    tracker.apply(trade("t1", "BUY", "100", "MATCHED"));
    assertEquals(0.0, tracker.apply(trade("t2", "SELL", "150", "MATCHED"))); // clamped at 0
  }
}
