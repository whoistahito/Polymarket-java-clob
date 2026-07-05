package com.polymarket.integration;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.*;
import com.polymarket.model.gamma.GammaMarketDetail;
import com.polymarket.model.gamma.MarketsRequest;
import com.polymarket.util.WalletUtils;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Domain support for {@link LiveSmokeTest}: value objects and services that model the live-test
 * domain, so the test itself reads as a narrative rather than a pile of static helpers.
 *
 * <p>Three value objects — {@link EnvConfig} (the run's configuration), {@link DepositWallet} (which
 * wallet trades and how it signs), {@link CanaryMarket} (the market we probe) — plus stateless
 * services that produce them from the live API. Nothing here places or cancels orders; that write
 * path stays in the test.
 */
final class LiveTestSupport {

    private LiveTestSupport() {
    }

    // -------------------------------------------------------------------------------------------
    // Value objects
    // -------------------------------------------------------------------------------------------

    /**
     * The run's configuration, entirely from environment variables (see {@link #fromEnv()}).
     */
    record EnvConfig(
            String privateKey,
            int chainId,
            BigDecimal maxSpend,
            int endWithinHours,
            String tokenIdOverride,
            String sigTypeOverride,
            String funderOverride,
            String testSizeOverride) {

        static EnvConfig fromEnv() {
            return new EnvConfig(
                    env("POLYMARKET_PRIVATE_KEY"),
                    Integer.parseInt(envOrDefault("POLYMARKET_CHAIN", "137")),
                    new BigDecimal(envOrDefault("POLYMARKET_MAX_SPEND", "1.00")),
                    Integer.parseInt(envOrDefault("POLYMARKET_END_WITHIN_HOURS", "6")),
                    env("POLYMARKET_TOKEN_ID"),
                    env("POLYMARKET_SIG_TYPE"),
                    env("POLYMARKET_FUNDER"),
                    env("POLYMARKET_TEST_SIZE"));
        }

        boolean hasPrivateKey() {
            return privateKey != null && !privateKey.isBlank();
        }
    }

    /**
     * A Polymarket deposit wallet: the maker/funder address and the signature type to sign with.
     */
    record DepositWallet(SignatureType sigType, String funder) {
    }

    /**
     * The market we canary on: token plus the metadata the order path needs.
     */
    record CanaryMarket(String tokenId, String tickSize, boolean negRisk, OrderBookSummary book) {
    }

    // -------------------------------------------------------------------------------------------
    // Client construction
    // -------------------------------------------------------------------------------------------

    static PolymarketClient.Builder clientBuilder(
            EnvConfig cfg, SignatureType sigType, String funder) {
        PolymarketClient.Builder b = new PolymarketClient.Builder()
                .privateKey(cfg.privateKey()).chainId(cfg.chainId()).signatureType(sigType);
        if (funder != null && !funder.isBlank()) {
            b.funderAddress(funder);
        }
        return b;
    }

    // -------------------------------------------------------------------------------------------
    // Deposit-wallet resolution
    // -------------------------------------------------------------------------------------------

    /**
     * Figures out which wallet to trade from. An explicit {@code POLYMARKET_FUNDER} wins; otherwise
     * derives the Safe and Proxy deposit wallets from the EOA and picks whichever actually holds
     * USDC. Returns null when no candidate is funded (so the test skips with guidance). Always logs
     * the derived addresses and balances first, so a failed run shows where the money actually is.
     */
    static DepositWallet resolveDeposit(PolymarketClient probe, EnvConfig cfg) throws Exception {
        String eoa = Credentials.create(cfg.privateKey()).getAddress();
        String proxy = WalletUtils.deriveProxyWallet(eoa, cfg.chainId()).orElse(null);
        String safe = WalletUtils.deriveSafeWallet(eoa, cfg.chainId()).orElse(null);

        log("[addr] EOA                = %s  (USDC %s)", eoa,
                usdcBalance(probe, new DepositWallet(SignatureType.EOA, eoa)));
        if (proxy != null)
            log("[addr] derived POLY_PROXY  = %s  (USDC %s)", proxy,
                    usdcBalance(probe, new DepositWallet(SignatureType.POLY_PROXY, proxy)));
        if (safe != null)
            log("[addr] derived GNOSIS_SAFE = %s  (USDC %s)", safe,
                    usdcBalance(probe, new DepositWallet(SignatureType.POLY_GNOSIS_SAFE, safe)));

        String envFunder = cfg.funderOverride();
        String envSigType = cfg.sigTypeOverride();

        if (envFunder != null && !envFunder.isBlank()) {
            if (!envFunder.matches("(?i)0x[0-9a-f]{40}")) {
                int hexLen = envFunder.replaceFirst("(?i)^0x", "").length();
                throw new IllegalArgumentException("POLYMARKET_FUNDER is not a valid 20-byte address "
                        + "(got " + hexLen + " hex chars, need 40) — likely truncated/mis-copied: " + envFunder);
            }
            SignatureType st = (envSigType != null && !envSigType.isBlank())
                    ? SignatureType.valueOf(envSigType) : SignatureType.POLY_GNOSIS_SAFE;
            log("[addr] pinned FUNDER       = %s  (%s | USDC %s)", envFunder, st,
                    usdcBalance(probe, new DepositWallet(st, envFunder)));
            return new DepositWallet(st, envFunder);
        }

        if ("EOA".equalsIgnoreCase(envSigType)) {
            return new DepositWallet(SignatureType.EOA, eoa); // user forced it (Polymarket usually rejects)
        }

        // Candidate deposit wallets derived from the EOA, gated on which one is funded.
        List<DepositWallet> candidates = new ArrayList<>();
        if (blankOrEquals(envSigType, "POLY_GNOSIS_SAFE") && safe != null) {
            candidates.add(new DepositWallet(SignatureType.POLY_GNOSIS_SAFE, safe));
        }
        if (blankOrEquals(envSigType, "POLY_PROXY") && proxy != null) {
            candidates.add(new DepositWallet(SignatureType.POLY_PROXY, proxy));
        }

        for (DepositWallet d : candidates) {
            BigDecimal bal = usdcBalance(probe, d);
            log("  deposit candidate %s %s -> USDC %s", d.sigType(), redact(d.funder()), bal);
            if (bal != null && bal.signum() > 0) return d;
        }
        return null;
    }

    static BigDecimal usdcBalance(PolymarketClient probe, DepositWallet d) {
        try {
            BalanceAllowanceResponse ba = probe.getBalanceAllowance(
                    BalanceAllowanceParams.builder()
                            .assetType(AssetType.COLLATERAL)
                            .signatureType(d.sigType())
                            .funderAddress(d.funder())
                            .build());
            String bal = ba.getBalance();
            return (bal != null && !bal.isBlank()) ? new BigDecimal(bal) : null;
        } catch (Exception e) {
            log("  balance check failed for %s: %s", d.funder(), e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------
    // Market discovery
    // -------------------------------------------------------------------------------------------

    /**
     * Resolves the market to canary on and loads its metadata. Uses {@code POLYMARKET_TOKEN_ID} if
     * pinned, else auto-picks the cheaper outcome of a liquid, tradeable market ending soon.
     * Returns null when nothing suitable is found.
     */
    static CanaryMarket discoverCanaryMarket(PolymarketClient client, EnvConfig cfg) throws Exception {
        String tokenId = cfg.tokenIdOverride();
        if (tokenId == null || tokenId.isBlank()) {
            tokenId = findCheapToken(client, cfg.endWithinHours());
            if (tokenId == null) {
                log("no tradeable market ends within %dh; falling back to any liquid market",
                        cfg.endWithinHours());
                tokenId = findCheapToken(client, 0);
            }
        }
        if (tokenId == null) return null;

        String tickSize = client.getTickSize(tokenId);
        boolean negRisk = client.getNegRisk(tokenId);
        OrderBookSummary book = client.getOrderBook(tokenId);
        return new CanaryMarket(tokenId, tickSize, negRisk, book);
    }

    private static String findCheapToken(PolymarketClient client, int endWithinHours) throws Exception {
        MarketsRequest.MarketsRequestBuilder rb =
                MarketsRequest.builder().closed(false).limit(100).volumeNumMin("1000");
        if (endWithinHours > 0) {
            rb.endDateMin(Instant.now().toString())
                    .endDateMax(Instant.now().plus(Duration.ofHours(endWithinHours)).toString());
        }
        List<GammaMarketDetail> markets = new ArrayList<>(client.gamma().markets(rb.build()));
        // Most liquid first, so the canary lands on a market with a real two-sided book.
        markets.sort(Comparator.comparing(
                (GammaMarketDetail m) -> m.volume24hr() != null ? m.volume24hr() : BigDecimal.ZERO).reversed());

        for (GammaMarketDetail m : markets) {
            if (!Boolean.TRUE.equals(m.acceptingOrders())) continue;
            if (!Boolean.TRUE.equals(m.enableOrderBook())) continue;
            if (Boolean.TRUE.equals(m.closed())) continue;
            List<String> tokens = nz(m.clobTokenIds());
            List<String> prices = nz(m.outcomePrices());
            if (tokens.size() < 2 || prices.size() < 2) continue;
            try {
                BigDecimal p0 = new BigDecimal(prices.get(0));
                BigDecimal p1 = new BigDecimal(prices.get(1));
                int cheap = p0.compareTo(p1) <= 0 ? 0 : 1;
                BigDecimal cp = cheap == 0 ? p0 : p1;
                // Genuinely cheap, and far enough above the tick that a tick-priced bid can't cross.
                if (cp.compareTo(new BigDecimal("0.03")) < 0 || cp.compareTo(new BigDecimal("0.60")) > 0) continue;
                log("picked \"%s\" — cheap side @ %s (vol24h=%s)", m.question(), cp, m.volume24hr());
                return tokens.get(cheap);
            } catch (NumberFormatException skip) {
                // market with malformed/empty outcome prices — ignore and keep looking
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------
    // Order-book queries
    // -------------------------------------------------------------------------------------------

    /**
     * Best ask = lowest sell price on the book, or null if there are no asks.
     */
    static BigDecimal bestAskPrice(List<OrderSummary> asks) {
        return extreme(asks, true);
    }

    /**
     * Best bid = highest buy price on the book, or null if there are no bids.
     */
    static BigDecimal bestBidPrice(List<OrderSummary> bids) {
        return extreme(bids, false);
    }

    private static BigDecimal extreme(List<OrderSummary> levels, boolean lowest) {
        if (levels == null || levels.isEmpty()) return null;
        BigDecimal best = null;
        for (OrderSummary l : levels) {
            BigDecimal p = new BigDecimal(l.getPrice());
            if (best == null || (lowest ? p.compareTo(best) < 0 : p.compareTo(best) > 0)) best = p;
        }
        return best;
    }

    static boolean containsOrder(List<OpenOrder> orders, String id) {
        return orders != null && orders.stream().anyMatch(o -> id.equals(o.getId()));
    }

    // -------------------------------------------------------------------------------------------
    // Misc utilities
    // -------------------------------------------------------------------------------------------

    static boolean sameCreds(ApiKeyCreds a, ApiKeyCreds b) {
        return a != null && b != null
                && a.getKey().equals(b.getKey())
                && a.getSecret().equals(b.getSecret())
                && a.getPassphrase().equals(b.getPassphrase());
    }

    static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    static <T> List<T> nz(List<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean blankOrEquals(String value, String expected) {
        return value == null || value.isBlank() || expected.equalsIgnoreCase(value);
    }

    static String env(String name) {
        return System.getenv(name);
    }

    static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    static void log(String fmt, Object... args) {
        System.out.println("[live-smoke] " + String.format(fmt, args));
    }

    static String redact(String value) {
        if (value == null) return "null";
        if (value.length() <= 6) return value;
        return value.substring(0, 6) + "...";
    }
}
