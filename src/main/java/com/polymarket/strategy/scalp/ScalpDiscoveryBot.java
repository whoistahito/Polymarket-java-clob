package com.polymarket.strategy.scalp;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.gamma.GammaMarketDetail;
import com.polymarket.model.gamma.MarketsRequest;
import com.polymarket.model.SignatureType;
import com.polymarket.util.Config;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-account discovery loop for the SHILIN-FLEET pre-window scalp — adapted to one wallet.
 *
 * <p>Polls Gamma for newly-listed BTC/ETH "Up or Down" 5m/15m markets, and for each one whose
 * measurement window opens within {@code entryLead} (default 180s), spawns a
 * {@link ScalpMarketSession} that works the cheap side until flat, then exits. The strategy is
 * market-neutral: it never bets on the up/down resolution, only on book convergence in the
 * seconds around window open. One shared {@link LiveScalpExecutor} + one async client serve all
 * concurrent sessions (the executor is stateless across markets apart from a per-market flatten
 * dedup set).
 *
 * <p>Wallet credentials come from {@code config.properties} via {@link Config} (same as
 * {@code ScalpBot}). Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.polymarket.strategy.scalp.ScalpDiscoveryBot" \
 *       -Dexec.args="-Dscalp.sizeUsdc=2 ..."
 * </pre>
 * Properties (all optional, defaults shown):
 * <ul>
 *   <li>{@code scalp.sizeUsdc=50} per-market notional</li>
 *   <li>{@code scalp.maxEntry=0.45}, {@code scalp.takeProfit=0.05}, {@code scalp.timeStopSec=60},
 *       {@code scalp.entryLeadSec=180}, {@code scalp.tick=0.01}</li>
 *   <li>{@code scalp.assets=Bitcoin,Ethereum} comma-separated asset names matched against the
 *       market question prefix</li>
 *   <li>{@code scalp.durations=5,15} comma-separated window lengths in minutes (parsed from the
 *       question's time range)</li>
 *   <li>{@code scalp.maxConcurrent=4} cap on simultaneously-worked markets</li>
 *   <li>{@code scalp.pollSec=20} discovery poll interval</li>
 *   <li>{@code scalp.limit=200} markets fetched per poll</li>
 * </ul>
 */
public final class ScalpDiscoveryBot {

    private static final Logger log = LoggerFactory.getLogger(ScalpDiscoveryBot.class);

    // "Bitcoin Up or Down - July 1, 4:45PM-4:50PM ET" → asset, start clock, end clock
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "^(?<asset>[A-Za-z]+) Up or Down - " +
        "\\p{Alpha}+\\s+\\d+,\\s+" +
        "(?<start>\\d{1,2}:\\d{2}(?:AM|PM))" +
        "-" +
        "(?<end>\\d{1,2}:\\d{2}(?:AM|PM))" +
        "\\s+ET$");

    private static final DateTimeFormatter END_DATE_FMT =
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");

    private volatile boolean running = true;
    private final PolymarketClient tradingClient;
    private final AsyncPolymarketClient asyncClient;
    private final LiveScalpExecutor executor;
    private final ApiKeyCreds creds;
    private final String walletAddress;
    private final ScalpConfig cfg;
    private final Set<Integer> allowedDurations;
    private final Pattern assetPattern;
    private final int maxConcurrent;
    private final int limit;
    private final long pollIntervalMs;
    private final long entryLeadMs;
    private final BigDecimal tickSize;

    private final ConcurrentHashMap<String, ScalpMarketSession> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService workPool;

    private ScalpDiscoveryBot(
        PolymarketClient tradingClient, AsyncPolymarketClient asyncClient,
        LiveScalpExecutor executor, ApiKeyCreds creds, String walletAddress,
        ScalpConfig cfg, Set<Integer> allowedDurations, Pattern assetPattern,
        int maxConcurrent, int limit, long pollIntervalMs, BigDecimal tickSize) {
        this.tradingClient = tradingClient;
        this.asyncClient = asyncClient;
        this.executor = executor;
        this.creds = creds;
        this.walletAddress = walletAddress;
        this.cfg = cfg;
        this.allowedDurations = allowedDurations;
        this.assetPattern = assetPattern;
        this.maxConcurrent = maxConcurrent;
        this.limit = limit;
        this.pollIntervalMs = pollIntervalMs;
        this.entryLeadMs = cfg.entryLeadMs();
        this.tickSize = tickSize;
        this.workPool = Executors.newFixedThreadPool(Math.max(8, maxConcurrent * 3));
    }

    public static void main(String[] args) throws Exception {
        ScalpParams p = ScalpParams.fromSystem();

        Config config = Config.load();
        String privateKey = config.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("credentials.private-key is not set in config.properties");
        }
        String funder = config.getFunderWallet();
        SignatureType sigType = config.getSignatureType();

        PolymarketClient initClient = new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .build();
        try {
            int v = initClient.resolveVersion();
            log.info("[scalp-discovery] CLOB order-protocol version: {}", v);
        } catch (Exception e) {
            log.warn("[scalp-discovery] could not resolve CLOB version up-front ({}); lazy resolve on first order", e.toString());
        }
        ApiKeyCreds creds = initClient.createOrDeriveApiKey();

        PolymarketClient.Builder tradingBuilder = new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .signatureType(sigType)
            .apiCreds(creds);
        if (funder != null && !funder.isBlank()) tradingBuilder.funderAddress(funder);
        PolymarketClient tradingClient = tradingBuilder.build();

        ExecutorService workPoolPlaceholder = Executors.newFixedThreadPool(Math.max(8, p.maxConcurrent * 3));
        AsyncPolymarketClient asyncClient = AsyncPolymarketClient.wrap(tradingClient, workPoolPlaceholder);
        LiveScalpExecutor executor = new LiveScalpExecutor(asyncClient, p.tickSize);

        String walletAddress = (funder != null && !funder.isBlank()) ? funder : initClient.getAddress();

        ScalpConfig cfg = new ScalpConfig(
            p.maxEntry, p.takeProfit, (long) (p.timeStopSec * 1000), p.sizeUsdc,
            (long) (p.entryLeadSec * 1000));

        Set<Integer> durations = new HashSet<>();
        for (String d : p.durations.split(",")) {
            try { durations.add(Integer.parseInt(d.trim())); } catch (NumberFormatException ignored) {}
        }

        Pattern assetPattern = Pattern.compile(
            "^(?:" + String.join("|", Arrays.asList(p.assets.split("\\s*,\\s*"))) + ") Up or Down -");

        ScalpDiscoveryBot bot = new ScalpDiscoveryBot(
            tradingClient, asyncClient, executor, creds, walletAddress, cfg,
            durations, assetPattern, p.maxConcurrent, p.limit,
            (long) (p.pollSec * 1000), p.tickSize);

        Runtime.getRuntime().addShutdownHook(new Thread(bot::shutdown, "scalp-discovery-shutdown"));
        log.info("[scalp-discovery] signer={} funder={} sigType={} wallet={} sizeUsdc={} assets={} durations={}",
            initClient.getAddress(), funder, sigType, walletAddress, p.sizeUsdc, p.assets, durations);
        bot.runLoop();
    }

    private void runLoop() {
        scheduler.scheduleWithFixedDelay(this::poll, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::reap, 5_000, 2_000, TimeUnit.MILLISECONDS);
        while (running) {
            try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        scheduler.shutdownNow();
        workPool.shutdown();
        log.info("[scalp-discovery] stopped. final active sessions={}", activeSessions.size());
    }

    /** Discovery poll: fetch soonest-closing active markets and spawn sessions for any new ones. */
    private void poll() {
        try {
            String endDateMin = Instant.now().toString(); // ISO-8601 UTC, e.g. 2026-07-01T20:43:00Z
            MarketsRequest req = MarketsRequest.builder()
                .closed(false)
                .order("endDate")
                .ascending(true)
                .limit(limit)
                .endDateMin(endDateMin)
                .build();
            List<GammaMarketDetail> markets = tradingClient.gamma().markets(req);
            long now = System.currentTimeMillis();
            for (GammaMarketDetail m : markets) {
                try {
                    consider(m, now);
                } catch (Exception e) {
                    log.warn("[scalp-discovery] consider failed for {}: {}", m.question(), e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[scalp-discovery] poll failed: {}", e.toString());
        }
    }

    private void consider(GammaMarketDetail m, long now) {
        if (m.question() == null) return;
        if (!assetPattern.matcher(m.question()).find()) return;
        if (!Boolean.TRUE.equals(m.active())) return;
        if (!Boolean.TRUE.equals(m.enableOrderBook())) return;
        if (!Boolean.TRUE.equals(m.acceptingOrders())) return;
        List<String> tokens = m.clobTokenIds();
        if (tokens == null || tokens.size() < 2) return;

        Matcher qm = QUESTION_PATTERN.matcher(m.question());
        if (!qm.matches()) return;
        int durationMin = minuteDelta(qm.group("start"), qm.group("end"));
        if (durationMin <= 0 || !allowedDurations.contains(durationMin)) return;

        Long windowCloseMs = parseEndDate(m.endDate());
        if (windowCloseMs == null) return;
        long windowOpenMs = windowCloseMs - durationMin * 60_000L;

        if (now < windowOpenMs - entryLeadMs) return;        // too early; revisit next poll
        if (now >= windowOpenMs) return;                      // window already open
        if (activeSessions.containsKey(m.conditionId())) return;
        if (activeSessions.size() >= maxConcurrent) {
            log.debug("[scalp-discovery] at cap {}/{}; skipping {} (window opens in {}s)",
                activeSessions.size(), maxConcurrent, m.question(), (windowOpenMs - now) / 1000);
            return;
        }

        spawn(m, tokens.get(0), tokens.get(1), windowOpenMs, windowCloseMs);
    }

    private void spawn(GammaMarketDetail m, String tokenA, String tokenB, long windowOpenMs, long windowCloseMs) {
        ScalpMarketSession session = new ScalpMarketSession(
            m.conditionId(), tokenA, tokenB, cfg, windowOpenMs, executor, creds, walletAddress);
        activeSessions.put(m.conditionId(), session);
        log.info("[scalp-discovery] SPAWN  {} | window opens in {}s (open={}, close={})",
            m.question(), (windowOpenMs - System.currentTimeMillis()) / 1000,
            Instant.ofEpochMilli(windowOpenMs), Instant.ofEpochMilli(windowCloseMs));
        workPool.submit(() -> {
            session.start();
            long deadline = windowCloseMs + 30_000;
            while (running && !session.isFinished() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(1_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (!session.isFinished()) {
                log.warn("[scalp-discovery] {} deadline reached without flat — forcing cancel", m.question());
                try { executor.cancelAll(m.conditionId(), tokenA); } catch (Exception ignored) {}
            }
            try { session.stop(); } catch (Exception ignored) {}
            activeSessions.remove(m.conditionId());
            log.info("[scalp-discovery] DONE    {} | final phase={}", m.question(), session.state().phase());
        });
    }

    /** Reap: defensively drop any session that has finished or blown past its window close. */
    private void reap() {
        long now = System.currentTimeMillis();
        activeSessions.forEach((id, s) -> {
            // ponytail: no per-session deadline tracking here; sessions self-reap in their worker
            // loop. This only catches sessions stuck past windowClose+30s whose worker thread died.
            if (s.isFinished()) {
                try { s.stop(); } catch (Exception ignored) {}
                activeSessions.remove(id);
            }
        });
    }

    private void shutdown() {
        log.info("[scalp-discovery] shutdown: cancelling {} active sessions", activeSessions.size());
        running = false;
        activeSessions.forEach((id, s) -> {
            try { s.stop(); } catch (Exception ignored) {}
        });
        // Best-effort cancels on server before threads die.
        activeSessions.forEach((id, s) -> {
            try { executor.cancelAll(id, ""); } catch (Exception ignored) {}
        });
        scheduler.shutdownNow();
        workPool.shutdown();
    }

    /** Difference in minutes between two 12-hour clock times like "4:45PM" and "4:50PM". */
    private static int minuteDelta(String start, String end) {
        try {
            int s = toMinutesSinceMidnight(start);
            int e = toMinutesSinceMidnight(end);
            int d = e - s;
            if (d < 0) d += 24 * 60; // window crossing midnight
            return d;
        } catch (Exception e) {
            return -1;
        }
    }

    private static int toMinutesSinceMidnight(String t) {
        // t = "4:45PM" or "12:30PM"
        boolean pm = t.endsWith("PM");
        String[] hm = t.substring(0, t.length() - 2).split(":");
        int h = Integer.parseInt(hm[0]);
        int m = Integer.parseInt(hm[1]);
        if (pm && h != 12) h += 12;
        if (!pm && h == 12) h = 0;
        return h * 60 + m;
    }

    /** Parses the Gamma {@code endDate} ("MM/dd/yyyy HH:mm:ss", UTC) to epoch millis, or null. */
    private static Long parseEndDate(String endDate) {
        if (endDate == null || endDate.isBlank()) return null;
        try {
            return LocalDateTime.parse(endDate, END_DATE_FMT).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    /** Parsed {@code -Dscalp.*} system properties. */
    private static final class ScalpParams {
        double maxEntry = 0.45;
        double takeProfit = 0.05;
        long timeStopSec = 60;
        double sizeUsdc = 2;
        long entryLeadSec = 180;
        BigDecimal tickSize = new BigDecimal("0.01");
        String assets = "Bitcoin,Ethereum";
        String durations = "5,15";
        int maxConcurrent = 4;
        int pollSec = 20;
        int limit = 200;

        static ScalpParams fromSystem() {
            ScalpParams p = new ScalpParams();
            p.maxEntry = dprop("scalp.maxEntry", p.maxEntry);
            p.takeProfit = dprop("scalp.takeProfit", p.takeProfit);
            p.timeStopSec = lprop("scalp.timeStopSec", p.timeStopSec);
            p.sizeUsdc = dprop("scalp.sizeUsdc", p.sizeUsdc);
            p.entryLeadSec = lprop("scalp.entryLeadSec", p.entryLeadSec);
            p.tickSize = new BigDecimal(sprop("scalp.tick", "0.01"));
            p.assets = sprop("scalp.assets", p.assets);
            p.durations = sprop("scalp.durations", p.durations);
            p.maxConcurrent = iprop("scalp.maxConcurrent", p.maxConcurrent);
            p.pollSec = iprop("scalp.pollSec", p.pollSec);
            p.limit = iprop("scalp.limit", p.limit);
            return p;
        }

        static double dprop(String k, double def) { String v = System.getProperty(k); return v == null || v.isBlank() ? def : Double.parseDouble(v.trim()); }
        static long lprop(String k, long def) { String v = System.getProperty(k); return v == null || v.isBlank() ? def : Long.parseLong(v.trim()); }
        static int iprop(String k, int def) { String v = System.getProperty(k); return v == null || v.isBlank() ? def : Integer.parseInt(v.trim()); }
        static String sprop(String k, String def) { String v = System.getProperty(k); return v == null || v.isBlank() ? def : v.trim(); }
    }
}