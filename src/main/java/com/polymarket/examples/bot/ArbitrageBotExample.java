package com.polymarket.examples.bot;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.AssetType;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.SignatureType;
import com.polymarket.util.Config;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main entry point for the High-Frequency Taker Arbitrage Bot Example. This sets up the
 * authentication, initializes the client, and boots the Orchestrator.
 */
public class ArbitrageBotExample {

  private static final int ASYNC_EXECUTOR_THREADS = 8;

  public static void main(String[] args) throws Exception {
    run();
  }

  private static void run() throws Exception {
    System.out.println("==================================================");
    System.out.println(" Polymarket Taker Arbitrage Bot");
    System.out.println("==================================================");

    // Load configuration
    Config config = Config.load();
    String privateKey = config.getPrivateKey();
    String funderAddress = config.getFunderWallet();
    SignatureType signatureType = config.getSignatureType();

    if (privateKey == null || privateKey.isBlank()) {
      System.err.println("ERROR: credentials.private-key is not set in config.properties.");
      System.err.println("Please set it to run the execution engine.");
      System.exit(1);
    }

    // 1) Build initial client and derive L2 credentials once at startup.
    PolymarketClient initClient =
        new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .build();

    // 2. Derive L2 API Keys (Required for placing real orders via the execution engine)
    System.out.println("Deriving L2 API Credentials...");
    ApiKeyCreds creds;
    try {
      creds = initClient.createOrDeriveApiKey();
      System.out.println("[OK] L2 API credentials successfully derived.");
    } catch (Exception e) {
      System.err.println("Failed to derive API Keys: " + e.getMessage());
      throw e;
    }

    // 3) Build the final trading client with L2 credentials.
    PolymarketClient.Builder tradingClientBuilder =
        new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .signatureType(signatureType)
            .apiCreds(creds);

    if (funderAddress != null && !funderAddress.isBlank()) {
      tradingClientBuilder.funderAddress(funderAddress);
    }

    PolymarketClient tradingClient = tradingClientBuilder.build();

    System.out.printf("Signer wallet: %s%n", initClient.getAddress());
    System.out.printf(
        "Funder wallet: %s%n",
        (funderAddress != null && !funderAddress.isBlank()) ? funderAddress : "(not set)");
    System.out.printf("Signature type: %s (%d)%n", signatureType, signatureType.getValue());

    // Fail fast if L2 auth is not usable (prevents noisy per-market 401 errors later).
    preflightL2Auth(tradingClient);

    // 4) Wrap with an Async client for non-blocking concurrent trade execution.
    ExecutorService executor = Executors.newFixedThreadPool(ASYNC_EXECUTOR_THREADS);
    AsyncPolymarketClient asyncClient = AsyncPolymarketClient.wrap(tradingClient, executor);

    // 5) Initialize the central Orchestrator.
    ArbitrageOrchestrator orchestrator = new ArbitrageOrchestrator(tradingClient, asyncClient);

    // Add a shutdown hook to guarantee we cleanly close WebSocket connections
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("\nShutdown signal received. Cleaning up...");
                  orchestrator.stop();
                  executor.shutdown();
                  System.out.println("Cleanup complete. Goodbye!");
                }));

    // 6) Start tracking and trading.
    orchestrator.start();

    System.out.println("\nBot is now active and polling for markets.");
    System.out.println("Press Ctrl+C to exit.\n");

    // Block main thread forever while the orchestrator runs its background threads
    Thread.currentThread().join();
  }

  private static void preflightL2Auth(PolymarketClient tradingClient) throws Exception {
    try {
      var response =
          tradingClient.getBalanceAllowance(
              BalanceAllowanceParams.builder().assetType(AssetType.COLLATERAL).build());
      BigDecimal balance = parseUsdc(response.getBalance());
      System.out.printf("[OK] L2 auth preflight passed. Collateral balance: %s USDC%n", balance);
    } catch (Exception e) {
      System.err.println("L2 auth preflight failed: " + e.getMessage());
      System.err.println(
          "Verify private key, chain.id, and that API creds were derived from the same signer address.");
      throw e;
    }
  }

  private static BigDecimal parseUsdc(String raw) {
    if (raw == null || raw.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(raw).scaleByPowerOfTen(-6);
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  private static String rootCauseMessage(Throwable error) {
    Throwable cursor = error;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage() != null ? cursor.getMessage() : cursor.toString();
  }
}
