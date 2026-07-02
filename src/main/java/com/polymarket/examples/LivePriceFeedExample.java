package com.polymarket.examples;

import com.polymarket.client.PolymarketClient;
import com.polymarket.model.GammaMarket;
import com.polymarket.util.Config;
import com.polymarket.ws.WsClient;
import com.polymarket.ws.WsMessageListener;
import com.polymarket.ws.model.LastTradePrice;
import com.polymarket.ws.model.MidpointUpdate;
import com.polymarket.ws.model.PriceChange;
import com.polymarket.ws.model.WsMessage;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interactive example — browse the top 5 active Polymarket markets, pick one,
 * then stream real-time price changes via WebSocket until you press Enter.
 *
 * <p>Credentials are read from {@code src/main/resources/config.properties}:
 * <pre>
 * credentials.private-key=0x...
 * </pre>
 *
 * <p>Run with:
 * <pre>
 * mvn exec:java -Dexec.mainClass="com.polymarket.examples.LivePriceFeedExample"
 * </pre>
 */
public class LivePriceFeedExample {

    private static final Logger log = LoggerFactory.getLogger(LivePriceFeedExample.class);

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        // ------------------------------------------------------------------ //
        // Step 1: Build client from config.properties
        // ------------------------------------------------------------------ //
        Config config = Config.load();
        String privateKey = config.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            System.err.println("ERROR: credentials.private-key is not set in config.properties.");
            System.exit(1);
        }

        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(privateKey)
                .chainId(config.getChainId())
                .build();

        // ------------------------------------------------------------------ //
        // Step 2: Fetch top 5 active markets via Gamma API
        // ------------------------------------------------------------------ //
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║      Polymarket Live Price Feed Example      ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        System.out.println("Fetching top markets...\n");

        Map<String, String> gammaParams = new HashMap<>();
        gammaParams.put("closed", "false");
        gammaParams.put("limit", "20");
        gammaParams.put("order", "volume24hr");
        gammaParams.put("ascending", "false");
        gammaParams.put("start_date_min", java.time.Instant.now().minus(1, ChronoUnit.DAYS).toString());
        List<GammaMarket> markets = client.getGammaMarkets(gammaParams).stream()
                .filter(GammaMarket::hasClobTokens)
                .limit(5)
                .toList();

        if (markets.isEmpty()) {
            System.out.println("No active markets found. Exiting.");
            return;
        }

        // ------------------------------------------------------------------ //
        // Step 3: Display the markets and ask user to pick one
        // ------------------------------------------------------------------ //
        System.out.println("Top 5 active markets by 24h volume:\n");
        for (int i = 0; i < markets.size(); i++) {
            GammaMarket m = markets.get(i);
            System.out.printf("  [%d] %s%n", i + 1, truncate(m.question(), 75));
            System.out.printf("      Volume 24h: %s  |  Closes: %s%n%n",
                    formatVolume(m.volume24hr()), formatDate(m.endDate()));
        }

        System.out.print("Enter a number [1–" + markets.size() + "]: ");
        Scanner scanner = new Scanner(System.in);
        int choice = -1;
        while (choice < 1 || choice > markets.size()) {
            String input = scanner.nextLine().trim();
            try {
                choice = Integer.parseInt(input);
                if (choice < 1 || choice > markets.size()) {
                    System.out.printf("Please enter a number between 1 and %d: ", markets.size());
                }
            } catch (NumberFormatException e) {
                System.out.printf("Please enter a number between 1 and %d: ", markets.size());
            }
        }

        GammaMarket chosen = markets.get(choice - 1);
        List<String> tokenIds = chosen.tokenIds();

        // ------------------------------------------------------------------ //
        // Step 4: Show initial prices before connecting
        // ------------------------------------------------------------------ //
        System.out.println("\n┌─────────────────────────────────────────────────┐");
        System.out.printf ("│ Market: %-40s│%n", truncate(chosen.question(), 40));
        System.out.println("└─────────────────────────────────────────────────┘");
        System.out.printf("  Tokens: %d outcome(s)%n%n", tokenIds.size());

        printTokenLabels(chosen);

        System.out.printf("  Fetching current prices for %d token(s)...%n%n", tokenIds.size());
        for (String tokenId : tokenIds) {
            try {
                java.math.BigDecimal buy  = client.getPrice(tokenId, "BUY");
                java.math.BigDecimal sell = client.getPrice(tokenId, "SELL");
                System.out.printf("  Token %-20s  BUY: %-6s  SELL: %-6s%n",
                        abbrev(tokenId), buy.toPlainString(), sell.toPlainString());
            } catch (Exception e) {
                System.out.printf("  Token %-20s  (price unavailable)%n", abbrev(tokenId));
            }
        }

        // ------------------------------------------------------------------ //
        // Step 5: Subscribe to WebSocket and stream price changes
        // ------------------------------------------------------------------ //
        System.out.println("\n──────────────────────────────────────────────────");
        System.out.println("  Connecting to WebSocket… Press [Enter] to stop.");
        System.out.println("──────────────────────────────────────────────────\n");

        CountDownLatch connected = new CountDownLatch(1);

        WsMessageListener listener = new WsMessageListener() {
            @Override
            public void onOpen() {
                System.out.println("  ✓ Connected. Waiting for events...\n");
                connected.countDown();
            }

            @Override
            public void onMessage(WsMessage message) {
                if (message instanceof PriceChange pc) {
                    handlePriceChange(pc, tokenIds);
                } else if (message instanceof LastTradePrice ltp) {
                    handleLastTradePrice(ltp, tokenIds);
                } else if (message instanceof MidpointUpdate mid) {
                    handleMidpoint(mid, tokenIds);
                }
            }

            @Override
            public void onError(Exception error) {
                System.err.println("  ✗ WebSocket error: " + error.getMessage());
            }

            @Override
            public void onClose(int code, String reason) {
                System.out.println("  WebSocket closed: " + code + " " + reason);
            }
        };

        WsClient ws = WsClient.builder()
                .listener(listener)
                .emitMidpointUpdates(true)
                .build();

        ws.subscribeMarket(tokenIds);
        connected.await(10, java.util.concurrent.TimeUnit.SECONDS);
        new Scanner(System.in).nextLine();

        // ------------------------------------------------------------------ //
        // Step 6: Graceful shutdown
        // ------------------------------------------------------------------ //
        System.out.println("\n  Disconnecting…");
        ws.close();
        System.out.println("  Done.");
    }

    // ----------------------------------------------------------------------- //
    // Event handlers                                                            //
    // ----------------------------------------------------------------------- //

    private static void handlePriceChange(PriceChange pc, List<String> trackedTokenIds) {
        if (pc.getPriceChanges() == null) return;
        for (var entry : pc.getPriceChanges()) {
            if (!trackedTokenIds.contains(entry.getAssetId())) continue;
            String side = entry.getSide() != null ? entry.getSide() : "   ";
            System.out.printf("  [PRICE]  token=%-20s  side=%-4s  price=%-8s%s%n",
                    abbrev(entry.getAssetId()), side, nvl(entry.getPrice()),
                    bestBidAsk(entry.getBestBid(), entry.getBestAsk()));
        }
    }

    private static void handleLastTradePrice(LastTradePrice ltp, List<String> trackedTokenIds) {
        if (!trackedTokenIds.contains(ltp.getAssetId())) return;
        System.out.printf("  [TRADE]  token=%-20s  price=%-8s  size=%-8s  side=%s%n",
                abbrev(ltp.getAssetId()), nvl(ltp.getPrice()),
                nvl(ltp.getSize()), nvl(ltp.getSide()));
    }

    private static void handleMidpoint(MidpointUpdate mid, List<String> trackedTokenIds) {
        if (!trackedTokenIds.contains(mid.getAssetId())) return;
        System.out.printf("  [MID]    token=%-20s  midpoint=%-8s%n",
                abbrev(mid.getAssetId()), nvl(mid.getMidpoint()));
    }

    // ----------------------------------------------------------------------- //
    // Display helpers                                                           //
    // ----------------------------------------------------------------------- //

    private static void printTokenLabels(GammaMarket market) {
        List<String> outcomes = market.outcomes();
        List<String> prices   = market.outcomePrices();
        List<String> tokenIds = market.tokenIds();
        for (int i = 0; i < tokenIds.size(); i++) {
            String outcome = (outcomes != null && i < outcomes.size()) ? outcomes.get(i) : "Outcome " + (i + 1);
            String price   = (prices   != null && i < prices.size())   ? prices.get(i)   : "?";
            System.out.printf("  Outcome %-4s  %-30s  current price: %s%n", (i + 1), outcome, price);
        }
        System.out.println();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String abbrev(String tokenId) {
        if (tokenId == null) return "?";
        return tokenId.length() > 18
                ? tokenId.substring(0, 8) + "…" + tokenId.substring(tokenId.length() - 6)
                : tokenId;
    }

    private static String nvl(String s) { return s != null ? s : "-"; }

    private static String bestBidAsk(String bid, String ask) {
        if (bid == null && ask == null) return "";
        return String.format("  (bid=%-6s ask=%-6s)", nvl(bid), nvl(ask));
    }

    private static String formatVolume(java.math.BigDecimal raw) {
        if (raw == null) return "N/A";
        double v = raw.doubleValue();
        if (v >= 1_000_000) return String.format("$%.1fM", v / 1_000_000);
        if (v >= 1_000)     return String.format("$%.1fK", v / 1_000);
        return String.format("$%.0f", v);
    }

    private static String formatDate(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("null")) return "N/A";
        return raw.length() >= 10 ? raw.substring(0, 10) : raw;
    }
}
