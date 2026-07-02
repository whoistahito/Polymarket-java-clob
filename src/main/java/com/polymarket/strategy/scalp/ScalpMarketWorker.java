package com.polymarket.strategy.scalp;

import com.polymarket.examples.bot.LocalOrderBook;
import com.polymarket.strategy.scalp.BookSnapshot.TokenQuote;
import com.polymarket.ws.WsMessageListener;
import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.WsMessage;
import java.util.Map;

/**
 * Drives one market through the scalp lifecycle. Maintains a {@link LocalOrderBook} per outcome
 * token from the market WebSocket feed, and on every tick asks {@link ScalpStrategy} what to do,
 * dispatching the resulting command to a {@link ScalpExecutor}. All decision logic lives in the
 * pure strategy; this class is only wiring.
 *
 * <p>Position is the current <em>net</em> holdings of the cheap token, pushed in absolute terms via
 * {@link #updatePosition} from the user-fill feed. Absolute (not delta) updates handle partial exit
 * fills and late fills naturally. De-duplication of a repeated flatten command is the executor's
 * job — it alone knows when an order actually resolves — so this class forwards every decision
 * verbatim.
 */
public class ScalpMarketWorker implements WsMessageListener {

  private final String marketId;
  private final String tokenIdA;
  private final String tokenIdB;
  private final ScalpConfig cfg;
  private final long windowOpenMs;
  private final ScalpExecutor executor;
  private final Map<String, LocalOrderBook> books;

  private volatile ScalpState state = ScalpState.initial();
  private double positionShares = 0;

  public ScalpMarketWorker(
      String marketId,
      String tokenIdA,
      String tokenIdB,
      ScalpConfig cfg,
      long windowOpenMs,
      ScalpExecutor executor) {
    this.marketId = marketId;
    this.tokenIdA = tokenIdA;
    this.tokenIdB = tokenIdB;
    this.cfg = cfg;
    this.windowOpenMs = windowOpenMs;
    this.executor = executor;
    this.books =
        Map.of(tokenIdA, new LocalOrderBook(tokenIdA), tokenIdB, new LocalOrderBook(tokenIdB));
  }

  /**
   * Report the current net holdings of {@code tokenId} (absolute, from the user-fill feed). Only
   * the cheap side we entered ever carries a position; updates for any other token are ignored.
   */
  public synchronized void updatePosition(String tokenId, double netShares) {
    if (tokenId != null && tokenId.equals(state.cheapTokenId())) {
      positionShares = Math.max(0, netShares);
      evaluate(System.currentTimeMillis());
    }
  }

  @Override
  public void onMessage(WsMessage message) {
    if (message instanceof BookUpdate update) {
      synchronized (this) {
        LocalOrderBook book = books.get(update.getAssetId());
        if (book == null) {
          return;
        }
        book.processUpdate(update);
        evaluate(System.currentTimeMillis());
      }
    }
  }

  @Override
  public void onError(Exception error) {}

  @Override
  public void onClose(int code, String reason) {}

  public ScalpState state() {
    return state;
  }

  double positionShares() {
    return positionShares;
  }

  /**
   * One decision cycle. Package-visible so tests can drive it with a controlled clock. Always calls
   * into the strategy, even once DONE — a late fill can still leave a residual position that DONE
   * must keep flattening (see {@link ScalpStrategy}).
   */
  synchronized void evaluate(long nowMs) {
    BookSnapshot snapshot = new BookSnapshot(quote(books.get(tokenIdA)), quote(books.get(tokenIdB)));
    ScalpStrategy.Result result =
        ScalpStrategy.decide(cfg, state, snapshot, positionShares, nowMs, windowOpenMs);
    dispatch(result.decision());
    state = result.nextState();
  }

  private void dispatch(ScalpDecision d) {
    switch (d.action()) {
      case NONE -> {}
      case PLACE_ENTRY -> executor.placeEntry(marketId, d.tokenId(), d.price(), d.sizeUsdc());
      case PLACE_EXIT -> executor.placeExit(marketId, d.tokenId(), d.price(), d.sizeShares());
      case MARKET_FLATTEN -> executor.marketFlatten(marketId, d.tokenId(), d.price(), d.sizeShares());
      case CANCEL_AND_DONE -> executor.cancelAll(marketId, d.tokenId());
    }
  }

  private static TokenQuote quote(LocalOrderBook book) {
    return new TokenQuote(
        book.getTokenId(), book.getBestBidPrice(), book.getBestAskPrice(), book.getBestAskSize());
  }
}
