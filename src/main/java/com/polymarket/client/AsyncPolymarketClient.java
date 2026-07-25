package com.polymarket.client;

import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.BanStatus;
import com.polymarket.model.BookParams;
import com.polymarket.model.BuilderApiKey;
import com.polymarket.model.BuilderApiKeyResponse;
import com.polymarket.model.BuilderTrade;
import com.polymarket.model.CreateOrderOptions;
import com.polymarket.model.DropNotificationParams;
import com.polymarket.model.GammaMarket;
import com.polymarket.model.HeartbeatResponse;
import com.polymarket.model.LastTradePriceResult;
import com.polymarket.model.MarketPrice;
import com.polymarket.model.MarketRules;
import com.polymarket.model.MarketReward;
import com.polymarket.model.MarketTradeEvent;
import com.polymarket.model.Notification;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.OpenOrderParams;
import com.polymarket.model.OrderBookSummary;
import com.polymarket.model.OrderMarketCancelParams;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderScoring;
import com.polymarket.model.OrderSubmission;
import com.polymarket.model.OrderType;
import com.polymarket.model.PaginationPayload;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.PriceHistoryFilterParams;
import com.polymarket.model.ReadonlyApiKeyResponse;
import com.polymarket.model.Side;
import com.polymarket.model.SignedOrder;
import com.polymarket.model.SpreadResult;
import com.polymarket.model.TotalUserEarning;
import com.polymarket.model.Trade;
import com.polymarket.model.TradeParams;
import com.polymarket.model.UserEarning;
import com.polymarket.model.UserMarketOrder;
import com.polymarket.model.UserOrder;
import com.polymarket.model.UserRewardsEarning;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async wrapper around {@link PolymarketClient}.
 *
 * <p>Every method returns a {@link CompletableFuture} that completes on the provided {@link
 * Executor} (defaults to {@link ForkJoinPool#commonPool()}). {@link IOException} and other
 * exceptions are propagated as {@link java.util.concurrent.CompletionException} in the returned
 * future.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * AsyncPolymarketClient async = AsyncPolymarketClient.wrap(client);
 * // or with custom executor:
 * AsyncPolymarketClient async = AsyncPolymarketClient.wrap(client, executor);
 *
 * async.getOrderBook(tokenId)
 *      .thenAccept(book -> System.out.println(book));
 * }</pre>
 */
public final class AsyncPolymarketClient {

  @FunctionalInterface
  private interface IoSupplier<T> {
    T get() throws IOException;
  }

  @FunctionalInterface
  private interface IoRunnable {
    void run() throws IOException;
  }

  private final PolymarketClient client;
  private final Executor executor;

  private AsyncPolymarketClient(PolymarketClient client, Executor executor) {
    this.client = Objects.requireNonNull(client, "client");
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  /**
   * Wrap a {@link PolymarketClient} using {@link ForkJoinPool#commonPool()} as executor.
   *
   * @param client the synchronous client to wrap
   * @return async wrapper
   */
  public static AsyncPolymarketClient wrap(PolymarketClient client) {
    return new AsyncPolymarketClient(client, ForkJoinPool.commonPool());
  }

  /**
   * Wrap a {@link PolymarketClient} with a custom {@link Executor}.
   *
   * @param client the synchronous client to wrap
   * @param executor executor on which async tasks run
   * @return async wrapper
   */
  public static AsyncPolymarketClient wrap(PolymarketClient client, Executor executor) {
    return new AsyncPolymarketClient(client, executor);
  }

  /** Returns the underlying synchronous client. */
  public PolymarketClient syncClient() {
    return client;
  }

  /** Returns the executor used for async tasks. */
  public Executor getExecutor() {
    return executor;
  }

  // -------------------------------------------------------------------------
  // Synchronous pass-through delegates (no I/O)
  // -------------------------------------------------------------------------

  public String getAddress() {
    return client.getAddress();
  }

  public int getChainId() {
    return client.getChainId();
  }

  public String getFunderAddress() {
    return client.getFunderAddress();
  }

  public boolean hasApiCreds() {
    return client.hasApiCreds();
  }

  public ApiKeyCreds getApiCreds() {
    return client.getApiCreds();
  }

  /** Clears tick-size, fee-rate, and neg-risk caches for all tokens (synchronous). */
  public void clearTickSizeCache() {
    client.clearTickSizeCache();
  }

  /** Removes cache entries for a single token (synchronous). */
  public void clearTickSizeCache(String tokenId) {
    client.clearTickSizeCache(tokenId);
  }

  /** Computes the SHA-1 fingerprint for an order-book snapshot (synchronous, no I/O). */
  public String getOrderBookHash(OrderBookSummary orderbook) {
    return client.getOrderBookHash(orderbook);
  }

  /** Walks the order book to compute a market price (synchronous, no I/O). */
  public BigDecimal calculateMarketPrice(
      Side side, BigDecimal amount, OrderType orderType, OrderBookSummary book) {
    return client.calculateMarketPrice(side, amount, orderType, book);
  }

  /** Returns the async RFQ sub-client. */
  public AsyncRfqClient rfq() {
    return new AsyncRfqClient(client.rfq(), executor);
  }

  /** Returns the Data API sub-client ({@code https://data-api.polymarket.com}). */
  public DataClient data() {
    return client.data();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private <T> CompletableFuture<T> async(IoSupplier<T> task) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return task.get();
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        },
        executor);
  }

  private CompletableFuture<Void> asyncVoid(IoRunnable task) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            task.run();
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        },
        executor);
  }

  // -------------------------------------------------------------------------
  // Utilities
  // -------------------------------------------------------------------------

  public CompletableFuture<Long> getServerTime() {
    return async(client::getServerTime);
  }

  // -------------------------------------------------------------------------
  // Auth
  // -------------------------------------------------------------------------

  public CompletableFuture<ApiKeyCreds> deriveApiKey() {
    return async(client::deriveApiKey);
  }

  public CompletableFuture<ApiKeyCreds> deriveApiKey(int nonce) {
    return async(() -> client.deriveApiKey(nonce));
  }

  public CompletableFuture<ApiKeyCreds> createApiKey() {
    return async(client::createApiKey);
  }

  public CompletableFuture<ApiKeyCreds> createApiKey(int nonce) {
    return async(() -> client.createApiKey(nonce));
  }

  public CompletableFuture<ApiKeyCreds> createOrDeriveApiKey() {
    return async(client::createOrDeriveApiKey);
  }

  public CompletableFuture<ApiKeyCreds> createOrDeriveApiKey(int nonce) {
    return async(() -> client.createOrDeriveApiKey(nonce));
  }

  // -------------------------------------------------------------------------
  // Market Data (public, no auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<Map<String, Object>> getMarkets(String nextCursor) {
    return async(() -> client.getMarkets(nextCursor));
  }

  public CompletableFuture<Map<String, Object>> getMarket(String conditionId) {
    return async(() -> client.getMarket(conditionId));
  }

  /**
   * Read a market's typed tick and minimum order size as exact {@link java.math.BigDecimal}s
   * (Ticket 024).
   *
   * @see PolymarketClient#getMarketRules(String)
   */
  public CompletableFuture<MarketRules> getMarketRules(String conditionId) {
    return async(() -> client.getMarketRules(conditionId));
  }

  public CompletableFuture<PaginationPayload<Map<String, Object>>> getSimplifiedMarkets(
      String nextCursor) {
    return async(() -> client.getSimplifiedMarkets(nextCursor));
  }

  public CompletableFuture<PaginationPayload<Map<String, Object>>> getSamplingMarkets(
      String nextCursor) {
    return async(() -> client.getSamplingMarkets(nextCursor));
  }

  public CompletableFuture<PaginationPayload<Map<String, Object>>> getSamplingSimplifiedMarkets(
      String nextCursor) {
    return async(() -> client.getSamplingSimplifiedMarkets(nextCursor));
  }

  public CompletableFuture<List<GammaMarket>> getGammaMarkets(Map<String, String> params) {
    return async(() -> client.getGammaMarkets(params));
  }

  public CompletableFuture<String> getTickSize(String tokenId) {
    return async(() -> client.getTickSize(tokenId));
  }

  public CompletableFuture<Integer> getFeeRateBps(String tokenId) {
    return async(() -> client.getFeeRateBps(tokenId));
  }

  public CompletableFuture<Boolean> getNegRisk(String tokenId) {
    return async(() -> client.getNegRisk(tokenId));
  }

  public CompletableFuture<OrderBookSummary> getOrderBook(String tokenId) {
    return async(() -> client.getOrderBook(tokenId));
  }

  public CompletableFuture<List<OrderBookSummary>> getOrderBooks(List<BookParams> params) {
    return async(() -> client.getOrderBooks(params));
  }

  public CompletableFuture<BigDecimal> getMidpoint(String tokenId) {
    return async(() -> client.getMidpoint(tokenId));
  }

  public CompletableFuture<Map<String, BigDecimal>> getMidpoints(List<BookParams> params) {
    return async(() -> client.getMidpoints(params));
  }

  public CompletableFuture<BigDecimal> getPrice(String tokenId, String side) {
    return async(() -> client.getPrice(tokenId, side));
  }

  public CompletableFuture<Map<String, Map<Side, BigDecimal>>> getPrices(List<BookParams> params) {
    return async(() -> client.getPrices(params));
  }

  public CompletableFuture<SpreadResult> getSpread(String tokenId) {
    return async(() -> client.getSpread(tokenId));
  }

  public CompletableFuture<Map<String, BigDecimal>> getSpreads(List<BookParams> params) {
    return async(() -> client.getSpreads(params));
  }

  public CompletableFuture<LastTradePriceResult> getLastTradePrice(String tokenId) {
    return async(() -> client.getLastTradePrice(tokenId));
  }

  public CompletableFuture<List<LastTradePriceResult>> getLastTradesPrices(
      List<BookParams> params) {
    return async(() -> client.getLastTradesPrices(params));
  }

  public CompletableFuture<Map<String, Object>> getOk() {
    return async(client::getOk);
  }

  public CompletableFuture<List<MarketPrice>> getPricesHistory(PriceHistoryFilterParams params) {
    return async(() -> client.getPricesHistory(params));
  }

  public CompletableFuture<List<MarketTradeEvent>> getMarketTradesEvents(String conditionId) {
    return async(() -> client.getMarketTradesEvents(conditionId));
  }

  public CompletableFuture<String> validateReadonlyApiKey(String address, String key) {
    return async(() -> client.validateReadonlyApiKey(address, key));
  }

  public CompletableFuture<List<MarketReward>> getCurrentRewards() {
    return async(client::getCurrentRewards);
  }

  public CompletableFuture<List<MarketReward>> getRawRewardsForMarket(String conditionId) {
    return async(() -> client.getRawRewardsForMarket(conditionId));
  }

  // -------------------------------------------------------------------------
  // Orders (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<OpenOrder> getOrder(String orderId) {
    return async(() -> client.getOrder(orderId));
  }

  public CompletableFuture<List<OpenOrder>> getOpenOrders() {
    return async(client::getOpenOrders);
  }

  public CompletableFuture<List<OpenOrder>> getOpenOrders(OpenOrderParams params) {
    return async(() -> client.getOpenOrders(params));
  }

  public CompletableFuture<List<OpenOrder>> getOpenOrders(Map<String, String> params) {
    return async(() -> client.getOpenOrders(params));
  }

  public CompletableFuture<OrderResponse> createAndPostOrder(
      String tokenId, Side side, BigDecimal price, BigDecimal size, OrderType orderType) {
    return async(() -> client.createAndPostOrder(tokenId, side, price, size, orderType));
  }

  public CompletableFuture<OrderResponse> createAndPostOrder(
      String tokenId,
      Side side,
      BigDecimal price,
      BigDecimal size,
      OrderType orderType,
      boolean postOnly,
      boolean deferExec) {
    return async(
        () ->
            client.createAndPostOrder(
                tokenId, side, price, size, orderType, postOnly, deferExec));
  }

  public CompletableFuture<List<OrderResponse>> createAndPostOrders(
      List<UserOrder> orders, OrderType orderType) {
    return async(() -> client.createAndPostOrders(orders, orderType));
  }

  public CompletableFuture<List<OrderResponse>> createAndPostOrders(
      List<UserOrder> orders, OrderType orderType, boolean postOnly, boolean deferExec) {
    return async(() -> client.createAndPostOrders(orders, orderType, postOnly, deferExec));
  }

  public CompletableFuture<OrderResponse> createAndPostMarketOrder(
      String tokenId, Side side, BigDecimal amount, OrderType orderType) {
    return async(() -> client.createAndPostMarketOrder(tokenId, side, amount, orderType));
  }

  public CompletableFuture<OrderResponse> postOrder(PostOrderPayload payload) {
    return async(() -> client.postOrder(payload));
  }

  public CompletableFuture<Map<String, Object>> postOrder(Map<String, Object> orderPayload) {
    return async(() -> client.postOrder(orderPayload));
  }

  public CompletableFuture<OrderResponse> postOrder(
      SignedOrder signedOrder, OrderType orderType, boolean postOnly, boolean deferExec) {
    return async(() -> client.postOrder(signedOrder, orderType, postOnly, deferExec));
  }

  /**
   * Fetch ONE page of open orders together with its cursor (Ticket 025).
   *
   * @see PolymarketClient#getOpenOrdersPaginated(Map, String)
   */
  public CompletableFuture<PaginationPayload<OpenOrder>> getOpenOrdersPaginated(
      Map<String, String> params, String nextCursor) {
    return async(() -> client.getOpenOrdersPaginated(params, nextCursor));
  }

  /**
   * Submit an order and complete with its typed disposition (Ticket 022).
   *
   * <p>The future completes normally for every exchange-side outcome — including transport loss,
   * which arrives as {@link com.polymarket.model.OrderSubmissionStatus#UNKNOWN} rather than an
   * exceptional completion, so a caller cannot mistake it for a rejection.
   */
  public CompletableFuture<OrderSubmission> submitOrder(PostOrderPayload payload) {
    return async(() -> client.submitOrder(payload));
  }

  /**
   * Submit a signed order and complete with its typed disposition (Ticket 022).
   *
   * @see #submitOrder(PostOrderPayload)
   */
  public CompletableFuture<OrderSubmission> submitOrder(
      SignedOrder signedOrder, OrderType orderType, boolean postOnly, boolean deferExec) {
    return async(() -> client.submitOrder(signedOrder, orderType, postOnly, deferExec));
  }

  public CompletableFuture<List<OrderResponse>> postOrders(List<PostOrderPayload> orderPayloads) {
    return async(() -> client.postOrders(orderPayloads));
  }

  public CompletableFuture<List<OrderResponse>> postOrders(
      List<SignedOrder> signedOrders, OrderType orderType, boolean postOnly, boolean deferExec) {
    return async(() -> client.postOrders(signedOrders, orderType, postOnly, deferExec));
  }

  public CompletableFuture<Map<String, Object>> cancelOrder(String orderId) {
    return async(() -> client.cancelOrder(orderId));
  }

  public CompletableFuture<Map<String, Object>> cancelOrders(List<String> orderIds) {
    return async(() -> client.cancelOrders(orderIds));
  }

  public CompletableFuture<Map<String, Object>> cancelAll() {
    return async(client::cancelAll);
  }

  public CompletableFuture<Map<String, Object>> cancelMarketOrders(
      OrderMarketCancelParams cancelParams) {
    return async(() -> client.cancelMarketOrders(cancelParams));
  }

  public CompletableFuture<SignedOrder> createOrder(UserOrder order, CreateOrderOptions options) {
    return async(() -> client.createOrder(order, options));
  }

  public CompletableFuture<SignedOrder> createMarketOrder(
      UserMarketOrder order, CreateOrderOptions options) {
    return async(() -> client.createMarketOrder(order, options));
  }

  // -------------------------------------------------------------------------
  // API Key Management (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<Map<String, Object>> getApiKeys() {
    return async(client::getApiKeys);
  }

  public CompletableFuture<Map<String, Object>> deleteApiKey() {
    return async(client::deleteApiKey);
  }

  // -------------------------------------------------------------------------
  // Account & Trades (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<BanStatus> getClosedOnlyMode() {
    return async(client::getClosedOnlyMode);
  }

  public CompletableFuture<List<Trade>> getTrades() {
    return async(client::getTrades);
  }

  public CompletableFuture<List<Trade>> getTrades(TradeParams params) {
    return async(() -> client.getTrades(params));
  }

  public CompletableFuture<List<Trade>> getTrades(Map<String, String> params) {
    return async(() -> client.getTrades(params));
  }

  public CompletableFuture<PaginationPayload<Trade>> getTradesPaginated(
      Map<String, String> params, String nextCursor) {
    return async(() -> client.getTradesPaginated(params, nextCursor));
  }

  public CompletableFuture<OrderScoring> isOrderScoring(String orderId) {
    return async(() -> client.isOrderScoring(orderId));
  }

  public CompletableFuture<Map<String, Boolean>> areOrdersScoring(List<String> orderIds) {
    return async(() -> client.areOrdersScoring(orderIds));
  }

  public CompletableFuture<Map<String, Boolean>> areOrdersScoring(
      com.polymarket.model.OrdersScoringParams params) {
    return async(() -> client.areOrdersScoring(params));
  }

  public CompletableFuture<BalanceAllowanceResponse> getBalanceAllowance(
      BalanceAllowanceParams params) {
    return async(() -> client.getBalanceAllowance(params));
  }

  public CompletableFuture<Void> updateBalanceAllowance(BalanceAllowanceParams params) {
    return asyncVoid(() -> client.updateBalanceAllowance(params));
  }

  public CompletableFuture<HeartbeatResponse> postHeartbeat(String heartbeatId) {
    return async(() -> client.postHeartbeat(heartbeatId));
  }

  /**
   * Starts automatic heartbeat posting with the default interval (5 seconds).
   *
   * <p>This is a synchronous lifecycle operation — it does not run asynchronously. The background
   * heartbeat task itself posts heartbeats on a timer thread.
   */
  public void startHeartbeats() {
    client.startHeartbeats();
  }

  /**
   * Starts automatic heartbeat posting with a custom interval.
   *
   * @param intervalMs milliseconds between heartbeat posts (must be &gt; 0)
   */
  public void startHeartbeats(long intervalMs) {
    client.startHeartbeats(intervalMs);
  }

  /** Stops automatic heartbeat posting. Does nothing if not active. */
  public void stopHeartbeats() {
    client.stopHeartbeats();
  }

  /** Returns {@code true} if automatic heartbeats are currently active. */
  public boolean isHeartbeatsActive() {
    return client.isHeartbeatsActive();
  }

  // -------------------------------------------------------------------------
  // Notifications (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<List<Notification>> getNotifications() {
    return async(client::getNotifications);
  }

  public CompletableFuture<Void> dropNotifications(DropNotificationParams params) {
    return asyncVoid(() -> client.dropNotifications(params));
  }

  // -------------------------------------------------------------------------
  // Rewards (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<List<UserEarning>> getEarningsForUserForDay(String date) {
    return async(() -> client.getEarningsForUserForDay(date));
  }

  public CompletableFuture<List<TotalUserEarning>> getTotalEarningsForUserForDay(String date) {
    return async(() -> client.getTotalEarningsForUserForDay(date));
  }

  public CompletableFuture<List<UserRewardsEarning>> getUserEarningsAndMarketsConfig(
      String date, String orderBy, String position, boolean noCompetition) {
    return async(
        () -> client.getUserEarningsAndMarketsConfig(date, orderBy, position, noCompetition));
  }

  public CompletableFuture<Map<String, BigDecimal>> getRewardPercentages() {
    return async(client::getRewardPercentages);
  }

  // -------------------------------------------------------------------------
  // Builder Trades (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<PaginationPayload<BuilderTrade>> getBuilderTrades(
      Map<String, String> params, String nextCursor) {
    return async(() -> client.getBuilderTrades(params, nextCursor));
  }

  // -------------------------------------------------------------------------
  // Readonly API Keys (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<ReadonlyApiKeyResponse> createReadonlyApiKey() {
    return async(client::createReadonlyApiKey);
  }

  public CompletableFuture<List<String>> getReadonlyApiKeys() {
    return async(client::getReadonlyApiKeys);
  }

  public CompletableFuture<Map<String, Object>> deleteReadonlyApiKey(String key) {
    return async(() -> client.deleteReadonlyApiKey(key));
  }

  // -------------------------------------------------------------------------
  // Builder API Keys (L2 auth)
  // -------------------------------------------------------------------------

  public CompletableFuture<BuilderApiKey> createBuilderApiKey() {
    return async(client::createBuilderApiKey);
  }

  public CompletableFuture<List<BuilderApiKeyResponse>> getBuilderApiKeys() {
    return async(client::getBuilderApiKeys);
  }

  public CompletableFuture<Map<String, Object>> revokeBuilderApiKey() {
    return async(client::revokeBuilderApiKey);
  }
}
