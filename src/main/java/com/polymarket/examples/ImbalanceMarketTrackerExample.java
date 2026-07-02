package com.polymarket.examples;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.*;
import com.polymarket.util.Config;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImbalanceMarketTrackerExample {

  public static void main(String[] args) {
    try {
      run();
    } catch (Exception e) {
      System.err.println("Fatal error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void run() throws Exception {
    Config config = Config.load();
    String privateKey = config.getPrivateKey();

    if (privateKey == null || privateKey.isBlank()) {
      System.err.println("ERROR: credentials.private-key is not set in config.properties.");
      System.exit(1);
    }

    PolymarketClient syncClient =
        new PolymarketClient.Builder().privateKey(privateKey).chainId(config.getChainId()).build();

    ExecutorService executor = Executors.newFixedThreadPool(8);
    AsyncPolymarketClient async = AsyncPolymarketClient.wrap(syncClient, executor);

    try {
      System.out.println("Fetching top active markets from Gamma API...");

      Map<String, String> gammaParams = new HashMap<>();
      gammaParams.put("closed", "false");
      gammaParams.put("limit", "25");
      gammaParams.put("order", "volume24hr");
      gammaParams.put("ascending", "true");
      gammaParams.put(
          "start_date_min", java.time.Instant.now().minus(1, ChronoUnit.DAYS).toString());

      List<GammaMarket> markets =
          syncClient.getGammaMarkets(gammaParams).stream()
              .filter(GammaMarket::hasClobTokens)
              .limit(25)
              .toList();

      if (markets.isEmpty()) {
        System.out.println("No active markets found. Exiting.");
        return;
      }

      System.out.printf("Found %d markets. Showing top 5:%n%n", markets.size());
      for (int i = 0; i < markets.size(); i++) {
        GammaMarket m = markets.get(i);
        System.out.printf("  [%d] %s%n", i + 1, truncate(m.question(), 72));
        System.out.printf(
            "      24h vol: %s  |  tokens: %d%n%n",
            formatVolume(m.volume24hr()), m.tokenIds().size());
      }

      List<CompletableFuture<Void>> bookFutures =
          markets.stream()
              .map(
                  market -> {
                    String tokenId = market.tokenIds().get(0);
                    return async
                        .getOrderBook(tokenId)
                        .thenCombine(
                            async.getMidpoint(tokenId),
                            (book, mid) -> {
                              printBookSummary(market, book, mid, market.volume24hr());
                              return null;
                            })
                        .exceptionally(
                            ex -> {
                              System.out.printf(
                                  "  %-40s  (unavailable: %s)%n",
                                  truncate(market.question(), 40), rootCause(ex).getMessage());
                              return null;
                            });
                  })
              .map(f -> f.thenAccept(ignored -> {}))
              .toList();

      CompletableFuture.allOf(bookFutures.toArray(new CompletableFuture[0])).join();

      // ---------------------------------------------------------------- //
      // Step 5 — Fetch executable BUY and SELL prices for all outcomes
      // ---------------------------------------------------------------- //
      System.out.println("\n──────────────────────────────────────────────────");
      System.out.println("Market executable prices (BUY/SELL per outcome)");
      System.out.println("──────────────────────────────────────────────────\n");

      List<CompletableFuture<Void>> priceFutures =
          markets.stream()
              .map(
                  market -> {
                    List<String> tokenIds = market.clobTokenIds();
                    List<String> outcomes = market.outcomes();
                    if (tokenIds == null || tokenIds.isEmpty()) {
                      System.out.printf(
                          "  %-40s  (missing token mapping)%n", truncate(market.question(), 40));
                      return CompletableFuture.<Void>completedFuture(null);
                    }

                    // /price side semantics: BUY -> best bid (price to sell), SELL -> best ask
                    // (price to buy)
                    List<CompletableFuture<BigDecimal>> buyFutures = new java.util.ArrayList<>();
                    List<CompletableFuture<BigDecimal>> sellFutures = new java.util.ArrayList<>();

                    for (String tokenId : tokenIds) {
                      buyFutures.add(async.getPrice(tokenId, "SELL"));
                      sellFutures.add(async.getPrice(tokenId, "BUY"));
                    }

                    List<CompletableFuture<BigDecimal>> allFutures = new java.util.ArrayList<>();
                    allFutures.addAll(buyFutures);
                    allFutures.addAll(sellFutures);

                    return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                        .thenAccept(
                            ignored -> {
                              StringBuilder sb = new StringBuilder();
                              sb.append(String.format("  %-40s ", truncate(market.question(), 40)));
                              for (int i = 0; i < tokenIds.size(); i++) {
                                String outcome =
                                    (outcomes != null && outcomes.size() > i)
                                        ? outcomes.get(i)
                                        : ("Outcome " + (i + 1));
                                BigDecimal buy = buyFutures.get(i).join();
                                BigDecimal sell = sellFutures.get(i).join();
                                sb.append(
                                    String.format(
                                        " %s(BUY=%s SELL=%s) ",
                                        outcome,
                                        buy != null ? buy.toPlainString() : "-",
                                        sell != null ? sell.toPlainString() : "-"));
                              }
                              System.out.println(sb.toString());
                            })
                        .exceptionally(
                            ex -> {
                              System.out.printf(
                                  "  %-40s  (unavailable)%n", truncate(market.question(), 40));
                              return null;
                            });
                  })
              .toList();

      CompletableFuture.allOf(priceFutures.toArray(new CompletableFuture[0])).join();

      // ---------------------------------------------------------------- //
      // Step 6 — L2 auth: open orders + balance (only if creds available)
      // ---------------------------------------------------------------- //
      System.out.println("\n──────────────────────────────────────────────────");
      System.out.println("Authenticated requests (L2 auth)");
      System.out.println("──────────────────────────────────────────────────\n");

      ApiKeyCreds creds = null;
      try {
        creds = syncClient.createOrDeriveApiKey();
        System.out.printf(
            "  API key derived: %s...%n",
            creds.getKey().substring(0, Math.min(12, creds.getKey().length())));
      } catch (Exception e) {
        System.out.println("  Could not obtain API credentials: " + e.getMessage());
        System.out.println("  (Skipping L2 steps)");
      }

      if (creds != null) {
        PolymarketClient tradingClient =
            new PolymarketClient.Builder()
                .privateKey(privateKey)
                .chainId(config.getChainId())
                .apiCreds(creds)
                .build();
        AsyncPolymarketClient authAsync = AsyncPolymarketClient.wrap(tradingClient, executor);

        String firstToken = markets.get(0).tokenIds().get(0);

        // Fire open-orders and balance queries in parallel
        CompletableFuture<List<OpenOrder>> ordersFuture =
            authAsync.getOpenOrders(OpenOrderParams.builder().assetId(firstToken).build());

        CompletableFuture<Void> balFuture =
            authAsync
                .getBalanceAllowance(BalanceAllowanceParams.builder().tokenId(firstToken).build())
                .thenAccept(
                    bal ->
                        System.out.printf(
                            "  Balance: %s  Allowance: %s%n", bal.getBalance(), bal.getAllowance()))
                .exceptionally(
                    ex -> {
                      System.out.println("  Balance unavailable: " + rootCause(ex).getMessage());
                      return null;
                    });

        ordersFuture
            .thenAccept(
                orders -> System.out.printf("  Open orders for first token: %d%n", orders.size()))
            .exceptionally(
                ex -> {
                  System.out.println("  Open orders unavailable: " + rootCause(ex).getMessage());
                  return null;
                });

        CompletableFuture.allOf(ordersFuture, balFuture).join();
      }

      System.out.println("\n✓ Done.");
    } finally {
      executor.shutdown();
    }
  }

  // ----------------------------------------------------------------------- //
  // Helpers
  // ----------------------------------------------------------------------- //

  private static void printBookSummary(
      GammaMarket market, OrderBookSummary book, BigDecimal mid, BigDecimal volume24hr) {
    int bids = book.getBids() != null ? book.getBids().size() : 0;
    int asks = book.getAsks() != null ? book.getAsks().size() : 0;
    System.out.printf(
        "  %-40s  tick=%-5s | bids=%-3d | asks=%-3d | mid=%s%n",
        truncate(market.question(), 40),
        book.getTickSize(),
        bids,
        asks,
        mid != null ? mid.toPlainString() : "-");
  }

  private static Throwable rootCause(Throwable t) {
    Throwable cause = t.getCause();
    return cause != null ? rootCause(cause) : t;
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  private static String formatVolume(BigDecimal raw) {
    if (raw == null) return "N/A";
    double v = raw.doubleValue();
    if (v >= 1_000_000) return String.format("$%.1fM", v / 1_000_000);
    if (v >= 1_000) return String.format("$%.1fK", v / 1_000);
    return String.format("$%.0f", v);
  }
}
