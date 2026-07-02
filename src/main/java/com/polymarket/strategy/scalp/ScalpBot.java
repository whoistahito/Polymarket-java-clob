package com.polymarket.strategy.scalp;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.SignatureType;
import com.polymarket.util.Config;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-wallet, single-market runnable entry point for the SHILIN-FLEET scalp. Point it at one
 * BTC/ETH up-or-down market and it works the pre-window scalp until flat, then exits.
 *
 * <p>Required system properties (or edit and pass explicitly):
 *
 * <pre>
 *   -Dscalp.market=&lt;conditionId&gt;         the market (condition id) to trade
 *   -Dscalp.tokenA=&lt;clobTokenId&gt;          first outcome token id
 *   -Dscalp.tokenB=&lt;clobTokenId&gt;          second outcome token id
 *   -Dscalp.windowOpen=&lt;iso-or-epochMs&gt;   measurement_start_time (must be flat by this instant)
 * </pre>
 *
 * <p>Optional (defaults match the spec): {@code scalp.maxEntry=0.45}, {@code scalp.takeProfit=0.05},
 * {@code scalp.timeStopSec=60}, {@code scalp.sizeUsdc=50}, {@code scalp.entryLeadSec=180},
 * {@code scalp.tick=0.01}. Wallet credentials come from {@code config.properties} via {@link
 * Config}, same as the arbitrage example.
 */
public final class ScalpBot {

  private static final Logger log = LoggerFactory.getLogger(ScalpBot.class);

  public static void main(String[] args) throws Exception {
    String marketId = requireProp("scalp.market");
    String tokenA = requireProp("scalp.tokenA");
    String tokenB = requireProp("scalp.tokenB");
    long windowOpenMs = parseInstant(requireProp("scalp.windowOpen"));

    ScalpConfig cfg =
        new ScalpConfig(
            doubleProp("scalp.maxEntry", 0.45),
            doubleProp("scalp.takeProfit", 0.05),
            (long) (doubleProp("scalp.timeStopSec", 60) * 1000),
            doubleProp("scalp.sizeUsdc", 50),
            (long) (doubleProp("scalp.entryLeadSec", 180) * 1000));

    if (windowOpenMs <= System.currentTimeMillis()) {
      log.error("scalp.windowOpen is in the past — nothing to trade. Aborting.");
      return;
    }

    Config config = Config.load();
    String privateKey = config.getPrivateKey();
    if (privateKey == null || privateKey.isBlank()) {
      log.error("credentials.private-key is not set in config.properties. Aborting.");
      return;
    }
    String funder = config.getFunderWallet();
    SignatureType sigType = config.getSignatureType();

    PolymarketClient initClient =
        new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .build();
    // PMK-004: resolve and cache the CLOB order-protocol version up-front so order signing
    // matches the deployment (V1 or V2). Previously this bot aborted on V2; the SDK now signs
    // both, so the preflight is just an informational log.
    try {
      int v = initClient.resolveVersion();
      log.info("[scalp] CLOB order-protocol version: {}", v);
    } catch (Exception e) {
      log.warn("[scalp] could not resolve CLOB version ({}); will resolve lazily on first order", e.toString());
    }
    ApiKeyCreds creds = initClient.createOrDeriveApiKey();

    PolymarketClient.Builder tradingBuilder =
        new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .signatureType(sigType)
            .apiCreds(creds);
    if (funder != null && !funder.isBlank()) {
      tradingBuilder.funderAddress(funder);
    }
    PolymarketClient tradingClient = tradingBuilder.build();
    String walletAddress = (funder != null && !funder.isBlank()) ? funder : initClient.getAddress();

    ExecutorService executor = Executors.newFixedThreadPool(4);
    AsyncPolymarketClient asyncClient = AsyncPolymarketClient.wrap(tradingClient, executor);
    LiveScalpExecutor scalpExecutor =
        new LiveScalpExecutor(asyncClient, new java.math.BigDecimal(prop("scalp.tick", "0.01")));

    ScalpMarketSession session =
        new ScalpMarketSession(
            marketId, tokenA, tokenB, cfg, windowOpenMs, scalpExecutor, creds, walletAddress);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("[scalp] shutdown: cancelling resting orders and closing feeds");
                  scalpExecutor.cancelAll(marketId, tokenA);
                  session.stop();
                }));

    log.info(
        "signer={} funder={} sigType={} market={} windowOpen={} ({}s away)",
        initClient.getAddress(), funder, sigType, marketId,
        Instant.ofEpochMilli(windowOpenMs), (windowOpenMs - System.currentTimeMillis()) / 1000);

    session.start();

    // Run until flat and finished, or a hard deadline shortly past window open (must be flat by then).
    long deadline = windowOpenMs + 30_000;
    while (!session.isFinished() && System.currentTimeMillis() < deadline) {
      Thread.sleep(1000);
    }

    if (!session.isFinished()) {
      log.warn("[scalp] deadline reached without confirmed flat — forcing cancel/close");
      scalpExecutor.cancelAll(marketId, tokenA);
    }
    session.stop();
    executor.shutdown();
    log.info("[scalp] done. final state={}", session.state().phase());
  }

  /**
   * This SDK only signs order-protocol V1 (see docs/tickets/PMK-001) — if the CLOB deployment has
   * negotiated V2, {@code createAndPostOrders} produces a signature the server silently rejects, so
   * the bot would appear to run while never actually trading. {@code GET /version}'s exact response
   * shape isn't confirmed here, so this check is deliberately fail-closed: abort on a clear V2
   * signal, warn (don't guess) on anything ambiguous. Bypass with {@code -Dscalp.skipVersionCheck=true}
   * only after manually confirming V1 signing is accepted on this deployment.
   */
  private static void preflightCheckOrderProtocolVersion(String clobHost) {
    if (Boolean.getBoolean("scalp.skipVersionCheck")) {
      log.warn("[scalp] order-protocol version check skipped by -Dscalp.skipVersionCheck=true");
      return;
    }
    try {
      java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5)).build();
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(clobHost + "/version"))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      String body = resp.body() == null ? "" : resp.body().trim();
      log.info("[scalp] GET {}/version -> {} '{}'", clobHost, resp.statusCode(), body);

      boolean looksV2 = body.matches(".*[\"']?version[\"']?\\s*:?\\s*2\\b.*") || body.equals("2");
      boolean looksV1 = body.matches(".*[\"']?version[\"']?\\s*:?\\s*1\\b.*") || body.equals("1");

      if (looksV2 && !looksV1) {
        throw new IllegalStateException(
            "CLOB deployment reports order-protocol V2 (raw: '" + body + "'). This SDK only signs "
                + "V1 orders (docs/tickets/PMK-001) — real orders would be silently rejected. "
                + "Refusing to start. Pass -Dscalp.skipVersionCheck=true only if you've manually "
                + "confirmed V1 signing is accepted here.");
      }
      if (!looksV1) {
        log.warn(
            "[scalp] Could not confirm order-protocol version from response body '{}'. This SDK "
                + "only signs V1 — if the deployment requires V2, orders will be silently rejected "
                + "(docs/tickets/PMK-001). Proceeding since the response wasn't a clear V2 signal.",
            body);
      }
    } catch (IllegalStateException e) {
      throw e; // clear V2 signal — real abort, not a connectivity fallback
    } catch (Exception e) {
      log.warn(
          "[scalp] Could not reach GET {}/version to confirm order-protocol version ({}). This SDK "
              + "only signs V1 orders — proceeding without confirmation.",
          clobHost, e.toString());
    }
  }

  private static String requireProp(String key) {
    String v = System.getProperty(key);
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException("Missing required system property: -D" + key);
    }
    return v;
  }

  private static String prop(String key, String def) {
    String v = System.getProperty(key);
    return (v == null || v.isBlank()) ? def : v;
  }

  private static double doubleProp(String key, double def) {
    String v = System.getProperty(key);
    return (v == null || v.isBlank()) ? def : Double.parseDouble(v);
  }

  /** Accepts either epoch milliseconds or an ISO-8601 instant (e.g. 2026-07-01T18:30:00Z). */
  private static long parseInstant(String s) {
    try {
      return Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return Instant.parse(s.trim()).toEpochMilli();
    }
  }

  private ScalpBot() {}
}
