package com.polymarket.examples.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.OrderBookLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalOrderBookTest {

  private LocalOrderBook book;

  @BeforeEach
  void setUp() {
    book = new LocalOrderBook("token-1");
  }

  private OrderBookLevel createLevel(String price, String size) {
    OrderBookLevel level = new OrderBookLevel();
    level.setPrice(price);
    level.setSize(size);
    return level;
  }

  @Test
  void testSnapshotInitialization() {
    BookUpdate update = new BookUpdate();
    List<OrderBookLevel> asks = new ArrayList<>();
    asks.add(createLevel("0.50", "100"));
    asks.add(createLevel("0.55", "50"));

    List<OrderBookLevel> bids = new ArrayList<>();
    bids.add(createLevel("0.45", "200"));
    bids.add(createLevel("0.40", "100"));

    update.setAsks(asks);
    update.setBids(bids);

    book.processUpdate(update);

    assertEquals(0.50, book.getBestAskPrice(), 0.0001);
    assertEquals(100.0, book.getBestAskSize(), 0.0001);

    assertEquals(0.45, book.getBestBidPrice(), 0.0001);
    assertEquals(200.0, book.getBestBidSize(), 0.0001);
  }

  @Test
  void testWebSocketDeltas_UpdateAndRemove() {
    // Initial state
    BookUpdate init = new BookUpdate();
    init.setAsks(new ArrayList<>(List.of(createLevel("0.50", "100"), createLevel("0.55", "50"))));
    book.processUpdate(init);

    assertEquals(0.50, book.getBestAskPrice(), 0.0001);

    // Update: Better ask arrives
    BookUpdate betterAsk = new BookUpdate();
    betterAsk.setAsks(new ArrayList<>(List.of(createLevel("0.48", "10"))));
    book.processUpdate(betterAsk);

    assertEquals(0.48, book.getBestAskPrice(), 0.0001);
    assertEquals(10.0, book.getBestAskSize(), 0.0001);

    // Update: Size goes to 0, removing the level
    BookUpdate removeAsk = new BookUpdate();
    removeAsk.setAsks(new ArrayList<>(List.of(createLevel("0.48", "0"))));
    book.processUpdate(removeAsk);

    // Should fall back to 0.50
    assertEquals(0.50, book.getBestAskPrice(), 0.0001);
    assertEquals(100.0, book.getBestAskSize(), 0.0001);

    // Update: 0.50 is partially filled
    BookUpdate partialFill = new BookUpdate();
    partialFill.setAsks(new ArrayList<>(List.of(createLevel("0.50", "30"))));
    book.processUpdate(partialFill);

    assertEquals(0.50, book.getBestAskPrice(), 0.0001);
    assertEquals(30.0, book.getBestAskSize(), 0.0001);
  }

  @Test
  void testLiquidityDepth() {
    BookUpdate init = new BookUpdate();
    init.setAsks(new ArrayList<>(List.of(createLevel("0.50", "50"), createLevel("0.55", "100"))));
    book.processUpdate(init);

    // Best ask is 0.50 for 50 shares. We want 100 shares.
    // 50 shares @ 0.50 = 25
    // 50 shares @ 0.55 = 27.5
    // Total cost = 52.5 for 100 shares -> 0.525 blended price
    assertEquals(0.525, book.getBlendedAskPrice(100.0), 0.0001);

    // Test insufficient liquidity
    assertEquals(Double.MAX_VALUE, book.getBlendedAskPrice(200.0));
  }
}
