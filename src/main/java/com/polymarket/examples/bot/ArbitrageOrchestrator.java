package com.polymarket.examples.bot;

import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.GammaMarket;
import com.polymarket.model.OrderBookSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The central manager responsible for market discovery and bot lifecycle management. Periodically
 * polls the Gamma API to discover new eligible markets, spawns asynchronous Arbitrage Bots, and
 * terminates them when markets resolve.
 */
public class ArbitrageOrchestrator {

  private final PolymarketClient syncClient;
  private final ExecutionEngine executionEngine;
  private final WalletInventory walletInventory;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final ConcurrentHashMap<String, MarketArbitrageBot> activeBots =
      new ConcurrentHashMap<>();

  // Configuration thresholds
  private static final BigDecimal MIN_VOLUME_24H =
      new BigDecimal("20000"); // Minimum $50k 24h volume
  private static final BigDecimal BOT_USD_ALLOCATION = new BigDecimal("5");
  private static final int POLL_INTERVAL_MINUTES = 5;
  private static final int MAX_BOTS_CONCURRENTLY = 1;

  public ArbitrageOrchestrator(PolymarketClient syncClient, AsyncPolymarketClient asyncClient) {
    this.syncClient = syncClient;
    this.walletInventory = new WalletInventory(syncClient, BOT_USD_ALLOCATION);
    this.executionEngine = new ExecutionEngine(asyncClient, walletInventory);
  }

  /** Starts the orchestrator background polling loop. */
  public void start() {
    // Initial run immediately, then repeat periodically
    scheduler.scheduleAtFixedRate(this::pollMarkets, 0, POLL_INTERVAL_MINUTES, TimeUnit.MINUTES);
  }

  /** Stops the orchestrator and cleanly shuts down all active bots. */
  public void stop() {
    scheduler.shutdown();

    activeBots.forEach(
        (marketId, bot) -> {
          bot.stop();
          walletInventory.release(marketId);
        });
    activeBots.clear();
    walletInventory.releaseAll();
  }

  /** Callback invoked by a bot when it detects its market has resolved or expired. */
  public void onMarketCompleted(String marketId) {
    MarketArbitrageBot bot = activeBots.remove(marketId);
    if (bot != null) {
      walletInventory.release(marketId);
    }
  }

  /** Polling logic to fetch and evaluate markets from the Gamma API. */
  private void pollMarkets() {
    try {
      Map<String, String> gammaParams = new HashMap<>();
      gammaParams.put("closed", "false");
      gammaParams.put("active", "true");
      gammaParams.put("limit", "50");
      gammaParams.put("offset", "10");
      gammaParams.put("order", "volume24hr");
      gammaParams.put("ascending", "false"); // High volume first
      gammaParams.put("start_date_min", Instant.now().minus(1, ChronoUnit.DAYS).toString());

      List<GammaMarket> markets = syncClient.getGammaMarkets(gammaParams);

      for (GammaMarket market : markets) {
        // Filter out markets without order books or CLOB tokens
        if (market.enableOrderBook() == null
            || !market.enableOrderBook()
            || !market.hasClobTokens()) {
          continue;
        }

        // Volume filter: Ensure the market has enough liquidity to be worth tracking
        if (market.volume24hr() == null || market.volume24hr().compareTo(MIN_VOLUME_24H) < 0) {
          continue;
        }

        // Fee filter: Only target fee-free markets. Taker arbitrage on fee-enabled markets
        // is rarely profitable unless the price dislocation is massive (>1.56% for some markets).
        // GammaMarket doesn't expose feesEnabled, so we query the CLOB API for the first token.
        // We also fetch the order book to obtain the per-market min_order_size.
        double minOrderSize;
        try {
          String sampleTokenId = market.tokenIds().get(0);
          int feeRateBps = syncClient.getFeeRateBps(sampleTokenId);
          if (feeRateBps > 0) {
            continue;
          }
          OrderBookSummary orderBook = syncClient.getOrderBook(sampleTokenId);
          minOrderSize = parseMinOrderSize(orderBook);
        } catch (Exception e) {
          System.err.printf(
              "[Orchestrator] Failed to check fee rate / order book for market=%s, skipping: %s%n",
              market.id(), e.getMessage());
          continue;
        }

        String marketId = market.id();

        // If a bot is already tracking this market, skip
        if (activeBots.containsKey(marketId)) {
          continue;
        }

        WalletInventory.AllocationDecision allocation = walletInventory.allocate(marketId);
        if (allocation == WalletInventory.AllocationDecision.INSUFFICIENT_BALANCE) {
          // Funds are exhausted for this cycle; remaining markets are skipped until next poll.
          break;
        }
        if (allocation != WalletInventory.AllocationDecision.ALLOCATED
            && allocation != WalletInventory.AllocationDecision.ALREADY_ALLOCATED) {
          continue;
        }

        spawnBot(market, minOrderSize);
      }

    } catch (Exception e) {
      System.err.println("Orchestrator failed to poll markets: " + e.getMessage());
    }
  }

  private void spawnBot(GammaMarket market, double minOrderSize) {
    String marketLabel =
            (market.question() != null && !market.question().isBlank())
                    ? market.question()
                    : market.id();
    System.out.println("Bot spawned: " + marketLabel);

    MarketArbitrageBot bot =
        new MarketArbitrageBot(
            market.id(),
            market.tokenIds(),
            market.outcomes(),
            marketLabel,
            minOrderSize,
            executionEngine,
            this::onMarketCompleted);

    activeBots.put(market.id(), bot);
    try {
      bot.start();
    } catch (RuntimeException e) {
      activeBots.remove(market.id());
      walletInventory.release(market.id());
      throw e;
    }
  }

  /** For testing purposes, returns the active bots map. */
  Map<String, MarketArbitrageBot> getActiveBots() {
    return activeBots;
  }

  /**
   * Parses the {@code min_order_size} from an order book summary, falling back to a safe default
   * of 1.0 if the value is missing or unparseable.
   */
  private static double parseMinOrderSize(OrderBookSummary orderBook) {
    if (orderBook == null || orderBook.getMinOrderSize() == null) {
      return 1.0;
    }
    try {
      double parsed = Double.parseDouble(orderBook.getMinOrderSize());
      return parsed > 0 ? parsed : 1.0;
    } catch (NumberFormatException e) {
      return 1.0;
    }
  }
}
