package com.polymarket.examples;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.GammaMarket;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.OpenOrderParams;
import com.polymarket.model.OrderBookSummary;
import com.polymarket.model.OrderType;
import com.polymarket.model.Side;
import com.polymarket.util.Config;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demonstrates {@link AsyncPolymarketClient} — the non-blocking
 * {@code CompletableFuture}-based wrapper around {@link PolymarketClient}.
 *
 * <p>The example:
 * <ol>
 *   <li>Fetches the top-5 active markets via the Gamma API (no credentials needed).</li>
 *   <li>Fires parallel async requests for order book, midpoint, BUY/SELL price, and spread
 *       across all 5 markets using {@link CompletableFuture#allOf}.</li>
 *   <li>If L2 credentials are present in {@code config.properties}, also shows an authenticated
 *       request (open orders and balance).</li>
 * </ol>
 *
 * <p>Credentials are read from {@code src/main/resources/config.properties}:
 * <pre>
 * credentials.private-key=0x...
 * </pre>
 *
 * <p>Run with:
 * <pre>
 * mvn exec:java -Dexec.mainClass="com.polymarket.examples.AsyncClientExample"
 * </pre>
 */
public class AsyncClientExample {

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

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║     AsyncPolymarketClient — Demo             ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        // ------------------------------------------------------------------ //
        // Step 1 — Build the synchronous client (no credentials needed yet)
        // ------------------------------------------------------------------ //
        PolymarketClient syncClient = new PolymarketClient.Builder()
                .privateKey(privateKey)
                .chainId(config.getChainId())
                .build();

        // ------------------------------------------------------------------ //
        // Step 2 — Wrap it with a dedicated thread pool
        // ------------------------------------------------------------------ //
        ExecutorService executor = Executors.newFixedThreadPool(8);
        AsyncPolymarketClient async = AsyncPolymarketClient.wrap(syncClient, executor);

        System.out.printf("Address : %s%n", async.getAddress());
        System.out.printf("Chain ID: %d%n%n", async.getChainId());

        try {
            // ---------------------------------------------------------------- //
            // Step 3 — Discover real markets via Gamma API
            // ---------------------------------------------------------------- //
            System.out.println("Fetching top active markets from Gamma API...");

            Map<String, String> gammaParams = new HashMap<>();
            gammaParams.put("closed", "false");
            gammaParams.put("limit", "20");
            gammaParams.put("order", "volume24hr");
            gammaParams.put("ascending", "false");
            gammaParams.put("start_date_min",
                    java.time.Instant.now().minus(1, ChronoUnit.DAYS).toString());

            List<GammaMarket> markets = syncClient.getGammaMarkets(gammaParams).stream()
                    .filter(GammaMarket::hasClobTokens)
                    .limit(5)
                    .toList();

            if (markets.isEmpty()) {
                System.out.println("No active markets found. Exiting.");
                return;
            }

            System.out.printf("Found %d markets. Showing top 5:%n%n", markets.size());
            for (int i = 0; i < markets.size(); i++) {
                GammaMarket m = markets.get(i);
                System.out.printf("  [%d] %s%n", i + 1, truncate(m.question(), 72));
                System.out.printf("      24h vol: %s  |  tokens: %d%n%n",
                        formatVolume(m.volume24hr()), m.tokenIds().size());
            }

            // ---------------------------------------------------------------- //
            // Step 4 — Fan-out: fetch order book for each market's first token
            //           in parallel, then print results as they arrive
            // ---------------------------------------------------------------- //
            System.out.println("──────────────────────────────────────────────────");
            System.out.println("Parallel async order-book fetch for all 5 markets");
            System.out.println("──────────────────────────────────────────────────\n");

            List<CompletableFuture<Void>> bookFutures = markets.stream().map(market -> {
                String tokenId = market.tokenIds().get(0);
                return async.getOrderBook(tokenId)
                        .thenCombine(async.getMidpoint(tokenId), (book, mid) -> {
                            printBookSummary(market, book, mid);
                            return null;
                        })
                        .exceptionally(ex -> {
                            System.out.printf("  %-40s  (unavailable: %s)%n",
                                    truncate(market.question(), 40),
                                    rootCause(ex).getMessage());
                            return null;
                        });
            }).map(f -> f.thenAccept(ignored -> {})).toList();

            CompletableFuture.allOf(bookFutures.toArray(new CompletableFuture[0])).join();

            // ---------------------------------------------------------------- //
            // Step 5 — Calculate market prices for the first token of each market
            // ---------------------------------------------------------------- //
            System.out.println("\n──────────────────────────────────────────────────");
            System.out.println("Market price estimates (FAK, $50 notional)");
            System.out.println("──────────────────────────────────────────────────\n");

            List<CompletableFuture<Void>> priceFutures = markets.stream().map(market -> {
                String tokenId = market.tokenIds().get(0);
                return async.getOrderBook(tokenId)
                        .thenAccept(book -> {
                            try {
                                BigDecimal buyPrice = syncClient.calculateMarketPrice(
                                        Side.BUY, new BigDecimal("50"), OrderType.FAK, book);
                                BigDecimal sellPrice = syncClient.calculateMarketPrice(
                                        Side.SELL, new BigDecimal("50"), OrderType.FAK, book);
                                System.out.printf("  %-40s  BUY=%-6s  SELL=%-6s%n",
                                        truncate(market.question(), 40), buyPrice, sellPrice);
                            } catch (Exception e) {
                                System.out.printf("  %-40s  (insufficient liquidity)%n",
                                        truncate(market.question(), 40));
                            }
                        })
                        .exceptionally(ex -> {
                            System.out.printf("  %-40s  (unavailable)%n",
                                    truncate(market.question(), 40));
                            return null;
                        });
            }).toList();

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
                System.out.printf("  API key derived: %s...%n",
                        creds.getKey().substring(0, Math.min(12, creds.getKey().length())));
            } catch (Exception e) {
                System.out.println("  Could not obtain API credentials: " + e.getMessage());
                System.out.println("  (Skipping L2 steps)");
            }

            if (creds != null) {
                PolymarketClient tradingClient = new PolymarketClient.Builder()
                        .privateKey(privateKey)
                        .chainId(config.getChainId())
                        .apiCreds(creds)
                        .build();
                AsyncPolymarketClient authAsync = AsyncPolymarketClient.wrap(tradingClient, executor);

                String firstToken = markets.get(0).tokenIds().get(0);

                // Fire open-orders and balance queries in parallel
                CompletableFuture<List<OpenOrder>> ordersFuture = authAsync.getOpenOrders(
                        OpenOrderParams.builder().assetId(firstToken).build());

                CompletableFuture<Void> balFuture = authAsync
                        .getBalanceAllowance(BalanceAllowanceParams.builder()
                                .tokenId(firstToken).build())
                        .thenAccept(bal -> System.out.printf(
                                "  Balance: %s  Allowance: %s%n",
                                bal.getBalance(), bal.getAllowance()))
                        .exceptionally(ex -> {
                            System.out.println("  Balance unavailable: " + rootCause(ex).getMessage());
                            return null;
                        });

                ordersFuture.thenAccept(orders ->
                        System.out.printf("  Open orders for first token: %d%n", orders.size()))
                        .exceptionally(ex -> {
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

    private static void printBookSummary(GammaMarket market, OrderBookSummary book, BigDecimal mid) {
        int bids = book.getBids() != null ? book.getBids().size() : 0;
        int asks = book.getAsks() != null ? book.getAsks().size() : 0;
        System.out.printf("  %-40s  tick=%-5s  bids=%-3d  asks=%-3d  mid=%s%n",
                truncate(market.question(), 40),
                book.getTickSize(),
                bids, asks,
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
        if (v >= 1_000)     return String.format("$%.1fK", v / 1_000);
        return String.format("$%.0f", v);
    }
}
