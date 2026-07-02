package com.polymarket.strategy.scalp;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.WsClient;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the scalp on one market end to end for a single wallet: subscribes the market order-book
 * feed (drives the {@link ScalpMarketWorker}) and the authenticated user-fill feed (updates the
 * worker's position), until the worker reaches DONE and is flat.
 *
 * <p>One {@link WsClient} serves both channels: the worker is its market listener, and a typed
 * {@code onTrade} callback feeds fills through a {@link ScalpFillTracker} into {@link
 * ScalpMarketWorker#updatePosition}.
 */
public class ScalpMarketSession {

  private static final Logger log = LoggerFactory.getLogger(ScalpMarketSession.class);

  private final String marketId;
  private final List<String> tokenIds;
  private final ScalpMarketWorker worker;
  private final ScalpFillTracker fillTracker = new ScalpFillTracker();
  private final ApiKeyCreds apiCreds;
  private final String walletAddress;

  /**
   * Time-based transitions (time-stop, window-open flatten) must fire even if the order book goes
   * quiet at the critical moment — the flat-by-window-open invariant can't depend on a book tick
   * arriving. This heartbeat re-evaluates on a fixed cadence as a safety net.
   */
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "scalp-heartbeat");
            t.setDaemon(true);
            return t;
          });

  private WsClient wsClient;

  public ScalpMarketSession(
      String marketId,
      String tokenIdA,
      String tokenIdB,
      ScalpConfig cfg,
      long windowOpenMs,
      ScalpExecutor executor,
      ApiKeyCreds apiCreds,
      String walletAddress) {
    this.marketId = marketId;
    this.tokenIds = List.of(tokenIdA, tokenIdB);
    this.worker = new ScalpMarketWorker(marketId, tokenIdA, tokenIdB, cfg, windowOpenMs, executor);
    this.apiCreds = apiCreds;
    this.walletAddress = walletAddress;
  }

  /** Subscribe both feeds and begin trading this market. */
  public synchronized void start() {
    if (wsClient != null) {
      return;
    }
    wsClient =
        WsClient.builder()
            .listener(worker)
            .apiKeyCreds(apiCreds)
            .walletAddress(walletAddress)
            .build();

    wsClient.subscribeMarket(tokenIds);
    wsClient.onTrade(List.of(marketId), this::onTrade);
    heartbeat.scheduleAtFixedRate(
        () -> worker.evaluate(System.currentTimeMillis()), 500, 500, TimeUnit.MILLISECONDS);
    log.info("[scalp] session started market={} tokens={}", marketId, tokenIds);
  }

  private void onTrade(com.polymarket.ws.model.TradeMessage trade) {
    Double net = fillTracker.apply(trade);
    if (net != null) {
      log.info(
          "[scalp] fill market={} token={} side={} size={} -> net={}",
          marketId, trade.getAssetId(), trade.getSide(), trade.getSize(), net);
      worker.updatePosition(trade.getAssetId(), net);
    }
  }

  public ScalpState state() {
    return worker.state();
  }

  /** True once the worker has finished and holds no position. */
  public boolean isFinished() {
    return worker.state().phase() == ScalpState.Phase.DONE && worker.positionShares() <= 0;
  }

  /** Cancel any resting orders and close the WebSocket. */
  public synchronized void stop() {
    heartbeat.shutdownNow();
    if (wsClient == null) {
      return;
    }
    wsClient.unsubscribeMarket(tokenIds);
    wsClient.close();
    wsClient = null;
    log.info("[scalp] session stopped market={}", marketId);
  }
}
