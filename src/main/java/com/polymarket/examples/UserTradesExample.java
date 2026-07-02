package com.polymarket.examples;

import com.polymarket.client.DataClient;
import com.polymarket.model.data.DataSide;
import com.polymarket.model.data.DataTrade;
import com.polymarket.model.data.DataTradesRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Interactively prompts for a wallet address and prints the user's trade history
 * from the Polymarket Data API — <strong>no API credentials required</strong>.
 *
 * <h3>Usage</h3>
 * <pre>
 * mvn exec:java -Dexec.mainClass="com.polymarket.examples.UserTradesExample"
 * </pre>
 */
public class UserTradesExample {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------

    private static void run() throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            Polymarket — User Trade History                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.print("  Enter wallet address (0x...): ");
        String user = scanner.nextLine().trim();

        if (!user.startsWith("0x") || user.length() != 42) {
            throw new IllegalArgumentException(
                "Invalid wallet address: '" + user + "'. Must be a 0x-prefixed 40-hex-char address.");
        }

        DataTradesRequest request = DataTradesRequest.builder().user(user).build();

        System.out.println("  Fetching trades …");
        System.out.println();

        DataClient client = new DataClient.Builder().build();

        List<DataTrade> trades = client.trades(request);

        // ------------------------------------------------------------------ //
        // Print
        // ------------------------------------------------------------------ //
        if (trades.isEmpty()) {
            System.out.println("  (no trades found for the given filters)\n");
            return;
        }

        printHeader();
        for (int i = 0; i < trades.size(); i++) {
            printTrade(i + 1, trades.get(i));
        }
        printFooter(trades);
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private static void printHeader() {
        System.out.printf("%-4s  %-19s  %-4s  %-10s  %-6s  %-30s  %s%n",
            "#", "Timestamp", "Side", "Size", "Price", "Market", "Outcome");
        System.out.println("─".repeat(100));
    }

    private static void printTrade(int index, DataTrade t) {
        String ts = t.getTimestamp() > 0
            ? TS_FMT.format(Instant.ofEpochSecond(t.getTimestamp()))
            : "-";

        String market = t.getTitle() != null
            ? truncate(t.getTitle(), 30)
            : abbrev(t.getConditionId());

        String outcome = t.getOutcome() != null ? t.getOutcome() : "-";
        String side = t.getSide() != null ? t.getSide().name() : "-";
        String size = t.getSize() != null ? t.getSize().toPlainString() : "-";
        String price = t.getPrice() != null ? t.getPrice().toPlainString() : "-";

        System.out.printf("%-4d  %-19s  %-4s  %-10s  %-6s  %-30s  %s%n",
            index, ts, side, size, price, market, outcome);

        // Show transaction hash on a second line for traceability
        if (t.getTransactionHash() != null) {
            System.out.printf("       tx: %s%n", abbrev(t.getTransactionHash()));
        }
    }

    private static void printFooter(List<DataTrade> trades) {
        System.out.println("─".repeat(100));

        // Aggregate stats
        BigDecimal totalBuy  = BigDecimal.ZERO;
        BigDecimal totalSell = BigDecimal.ZERO;
        int buyCount  = 0;
        int sellCount = 0;

        for (DataTrade t : trades) {
            if (t.getSize() != null && t.getSide() != null) {
                if (t.getSide() == DataSide.BUY) {
                    totalBuy = totalBuy.add(t.getSize());
                    buyCount++;
                } else {
                    totalSell = totalSell.add(t.getSize());
                    sellCount++;
                }
            }
        }

        System.out.printf(
            "  Total: %d trade(s)  |  BUY: %d (volume %s)  |  SELL: %d (volume %s)%n%n",
            trades.size(),
            buyCount,  totalBuy.stripTrailingZeros().toPlainString(),
            sellCount, totalSell.stripTrailingZeros().toPlainString());
    }

    // -------------------------------------------------------------------------

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String abbrev(String s) {
        if (s == null) return "-";
        return s.length() > 18 ? s.substring(0, 8) + "…" + s.substring(s.length() - 6) : s;
    }
}
