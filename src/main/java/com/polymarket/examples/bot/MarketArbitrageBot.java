package com.polymarket.examples.bot;

import com.polymarket.ws.WsClient;
import com.polymarket.ws.WsMessageListener;
import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.WsMessage;

import java.util.*;
import java.util.function.Consumer;

/**
 * A dedicated, asynchronous worker evaluating a single market for arbitrage opportunities.
 * Subscribes to the order book for all tokens in a market, maintains a local in-memory order book,
 * and evaluates execution conditions on every tick.
 */
public class MarketArbitrageBot implements WsMessageListener {

  private static final boolean DEBUG_EVALUATION =
      Boolean.parseBoolean(System.getProperty("bot.debug.evaluation", "true"));

  private record MarketSide(String tokenId, String outcomeLabel) {}

  private record SideQuote(
      String tokenId, String outcomeLabel, double buyPrice, double sellPrice, double buySize) {}

  private final String marketId;
  private final String marketLabel;
  private final List<String> tokenIds;
  private final List<MarketSide> sides;
  private final ExecutionEngine executionEngine;
  private final Consumer<String> onMarketCompleted;
  private final Map<String, LocalOrderBook> orderBooks;

  private WsClient wsClient;
  private boolean isRunning = false;

  // Strategy parameters
  private static final double MAX_TOTAL_BUY_COST =
      1.00; // Execute only when combined BUY sum < 1.00
  private static final double DEFAULT_MIN_TRADE_SIZE = 5.0; // Fallback if not supplied
  /** Minimum USD cost per individual leg. Polymarket rejects orders below ~$1. */
  private static final double MIN_LEG_COST_USD = 1.0;

  /** Actual minimum trade size for this market, sourced from the order book API. */
  private final double minTradeSize;

  public MarketArbitrageBot(
      String marketId,
      List<String> tokenIds,
      ExecutionEngine executionEngine,
      Consumer<String> onMarketCompleted) {
    this(marketId, tokenIds, null, marketId, 0, executionEngine, onMarketCompleted);
  }

  public MarketArbitrageBot(
      String marketId,
      List<String> tokenIds,
      List<String> outcomes,
      String marketLabel,
      ExecutionEngine executionEngine,
      Consumer<String> onMarketCompleted) {
    this(marketId, tokenIds, outcomes, marketLabel, 0, executionEngine, onMarketCompleted);
  }

  public MarketArbitrageBot(
      String marketId,
      List<String> tokenIds,
      List<String> outcomes,
      String marketLabel,
      double minOrderSize,
      ExecutionEngine executionEngine,
      Consumer<String> onMarketCompleted) {
    this.marketId = marketId;
    this.marketLabel = marketLabel != null && !marketLabel.isBlank() ? marketLabel : marketId;
    this.minTradeSize = minOrderSize > 0 ? minOrderSize : DEFAULT_MIN_TRADE_SIZE;
    this.executionEngine = executionEngine;
    this.onMarketCompleted = onMarketCompleted;
    this.orderBooks = new HashMap<>();
    this.sides = new ArrayList<>();

    List<String> safeTokenIds = tokenIds != null ? tokenIds : List.of();
    List<String> safeOutcomes = outcomes != null ? outcomes : List.of();
    Set<String> seenTokenIds = new HashSet<>();

    for (int i = 0; i < safeTokenIds.size(); i++) {
      String tokenId = safeTokenIds.get(i);
      if (tokenId == null || tokenId.isBlank()) {
        continue;
      }

      if (!seenTokenIds.add(tokenId)) {
        System.err.printf(
            "[BOT][%s] Skipping duplicate outcome tokenId=%s to avoid submitting repeated legs.%n",
            marketLabel, tokenId);
        continue;
      }

      String outcomeLabel =
          (i < safeOutcomes.size() && safeOutcomes.get(i) != null && !safeOutcomes.get(i).isBlank())
              ? safeOutcomes.get(i)
              : "Outcome " + (i + 1);

      this.sides.add(new MarketSide(tokenId, outcomeLabel));
      this.orderBooks.put(tokenId, new LocalOrderBook(tokenId));
    }

    this.tokenIds = this.sides.stream().map(MarketSide::tokenId).toList();
  }

  public MarketArbitrageBot(
      String marketId,
      List<String> tokenIds,
      List<String> outcomes,
      ExecutionEngine executionEngine,
      Consumer<String> onMarketCompleted) {
    this(marketId, tokenIds, outcomes, marketId, 0, executionEngine, onMarketCompleted);
  }

  /** Starts the bot, connecting to the WebSocket and subscribing to token order books. */
  public synchronized void start() {
    if (isRunning) return;

    if (tokenIds.size() < 2) {
      System.err.printf(
          "[BOT][%s] Market has fewer than 2 unique tokens; arbitrage bot will not run.%n",
          marketLabel);
      onMarketCompleted.accept(marketId);
      return;
    }

    executionEngine.setPartialFillCallback(this::handlePartialFill);

    // Initialize WebSocket Client
    wsClient = WsClient.builder().listener(this).build();

    // Subscribe to the market channel for all tokens in this market
    wsClient.subscribeMarket(tokenIds);
    isRunning = true;
  }

  private void handlePartialFill(
      List<String> filledTokenIds, List<Double> currentBidPrices, List<Double> sizes) {
    if (filledTokenIds == null || filledTokenIds.isEmpty()) {
      return;
    }

    System.out.printf(
        "[BOT][%s] Closing %d partial fill position(s) immediately%n",
        marketLabel, filledTokenIds.size());

    for (int i = 0; i < filledTokenIds.size(); i++) {
      String tokenId = filledTokenIds.get(i);
      double size = sizes.get(i);

      double currentBidPrice =
              (currentBidPrices != null && i < currentBidPrices.size())
                      ? currentBidPrices.get(i)
                      : getBestBidOrZero(tokenId);

      if (currentBidPrice > 0 && currentBidPrice != Double.MAX_VALUE) {
        executionEngine.sellPosition(marketId, tokenId, size, currentBidPrice);
      } else {
        System.err.printf(
            "[BOT][%s] Cannot close position: no bid available for token=%s%n",
            marketLabel, tokenId);
      }
    }
  }

  private double getBestBidOrZero(String tokenId) {
    LocalOrderBook book = orderBooks.get(tokenId);
    return book != null ? book.getBestBidPrice() : 0.0;
  }

  /** Stops the bot and disconnects the WebSocket. */
  public synchronized void stop() {
    if (!isRunning) return;

    if (wsClient != null) {
      wsClient.unsubscribeMarket(tokenIds);
    }
    isRunning = false;
  }

  @Override
  public void onMessage(WsMessage message) {
    if (message instanceof BookUpdate update) {
      String assetId = update.getAssetId();

      if (assetId != null && orderBooks.containsKey(assetId)) {
        // Synchronize to prevent race conditions during evaluation across multiple token updates
        synchronized (this) {
          LocalOrderBook book = orderBooks.get(assetId);
          book.processUpdate(update);

          evaluateArbitrage();
        }
      }
    }
  }

  @Override
  public void onError(Exception error) {
    // Keep runtime output intentionally quiet.
  }

  @Override
  public void onClose(int code, String reason) {
    // Automatic reconnect logic could be handled here or relied upon the SDK's internal mechanisms.
  }

  /**
   * Core strategy logic. Evaluates the current state of all local order books. If the sum of the
   * best asks is below the threshold, it triggers the ExecutionEngine.
   */
  private void evaluateArbitrage() {
    double totalBuyCost = 0.0;
    double minAvailableSize = Double.MAX_VALUE;
    List<SideQuote> quotes = new ArrayList<>();

    for (MarketSide side : sides) {
      LocalOrderBook book = orderBooks.get(side.tokenId());
      if (book == null) {
        return;
      }

      // BUY price is best ask (price we pay to buy); SELL price is best bid.
      double buyPrice = book.getBestAskPrice();
      double buySize = book.getBestAskSize();
      double sellPrice = book.getBestBidPrice();

      // If a side has no asks, we can't calculate a complete arbitrage
      if (buyPrice == Double.MAX_VALUE) {
        return;
      }

      // Detect if market is resolving or already fully priced out
      if (sellPrice >= 0.99 || buyPrice >= 1.0 || buyPrice == 0.0) {
        String closeReason =
            "side='"
                + side.outcomeLabel()
                + "' sell="
                + String.format("%.4f", sellPrice)
                + " buy="
                + String.format("%.4f", buyPrice)
                + " (interpreted as resolved/untradeable)";
        System.out.println("Market closed: " + marketLabel + " | " + closeReason);
        stop();
        onMarketCompleted.accept(marketId);
        return;
      }

      quotes.add(new SideQuote(side.tokenId(), side.outcomeLabel(), buyPrice, sellPrice, buySize));
      totalBuyCost += buyPrice;
      minAvailableSize = Math.min(minAvailableSize, buySize);
    }

    if (quotes.size() < 2) {
      return;
    }

    // Check if the arbitrage condition is met.
    if (totalBuyCost < MAX_TOTAL_BUY_COST && minAvailableSize >= minTradeSize) {

      // Guard: every leg must meet the minimum USD cost threshold.
      // Polymarket rejects orders where price × size is below ~$1.
      for (SideQuote quote : quotes) {
        double legCost = quote.buyPrice() * minAvailableSize;
        if (legCost < MIN_LEG_COST_USD) {
          if (DEBUG_EVALUATION) {
            System.out.printf(
                "[BOT][%s] Skipping opportunity: leg '%s' cost=%.4f < MIN_LEG_COST_USD=%.2f%n",
                marketLabel, quote.outcomeLabel(), legCost, MIN_LEG_COST_USD);
          }
          return;
        }
      }

      System.out.printf(
          "Opportunity detected: %s | outcomes=%d | totalBuy=%.4f | maxSize=%.2f%n",
          marketLabel, sides.size(), totalBuyCost, minAvailableSize);

      if (DEBUG_EVALUATION) {
        for (SideQuote quote : quotes) {
          System.out.printf(
              "[BOT][%s] Quote | outcome=%s | buy=%.6f | sell=%.6f | askSize=%.4f%n",
              marketLabel,
              quote.outcomeLabel(),
              quote.buyPrice(),
              quote.sellPrice(),
              quote.buySize());
        }
      }

      List<ExecutionEngine.TradeInstruction> instructions = new ArrayList<>();
      for (SideQuote quote : quotes) {
        instructions.add(
            new ExecutionEngine.TradeInstruction(
                quote.tokenId(), quote.buyPrice(), minAvailableSize));
      }

      if (DEBUG_EVALUATION) {
        System.out.printf(
                "[BOT][%s] Dispatching execution batch with %d outcome legs.%n",
                marketLabel, instructions.size());
      }

      // Trigger the concurrent execution engine
      executionEngine.executeArbitrage(marketId, instructions);
    }
  }
}
