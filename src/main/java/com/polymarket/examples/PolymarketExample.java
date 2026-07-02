package com.polymarket.examples;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.OrderBuilder;
import com.polymarket.client.PolymarketClient;
import com.polymarket.client.ProxyConfig;
import com.polymarket.model.CreateOrderOptions;
import com.polymarket.model.OrderType;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.Side;
import com.polymarket.model.SignedOrder;
import com.polymarket.model.SpreadResult;
import com.polymarket.model.UserOrder;
import com.polymarket.util.Config;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;

/**
 * Example demonstrating the Polymarket Java client usage.
 *
 * <p>Usage:
 * <pre>
 * # Set environment variables
 * set PRIVATE_KEY=0x...
 * set FUNDER_ADDRESS=0x...  (optional, your Polymarket wallet address)
 *
 * # Run
 * mvn exec:java -Dexec.mainClass="com.polymarket.arbitrage.PolymarketExample"
 * </pre>
 *
 * <p>SECURITY NOTE: Never hardcode private keys in source code.
 * Always use environment variables or secure key management.
 */
public class PolymarketExample {

    private static final Logger log = LoggerFactory.getLogger(
        PolymarketExample.class
    );

    public static void main(String[] args) {
        // Read credentials from environment variables - NEVER hardcode private keys!
        String privateKey = Config.load().getPrivateKey();
        String funderAddress = Config.load().getFunderWallet();

        if (privateKey == null || privateKey.isEmpty()) {
            log.error("PRIVATE_KEY environment variable is required");
            System.exit(1);
        }

        try {
            runExample(privateKey, funderAddress);
        } catch (Exception e) {
            log.error("Error running example", e);
            System.exit(1);
        }
    }

    private static void runExample(String privateKey, String funderAddress)
        throws IOException {
        log.info("=== Polymarket Java Client Example ===\n");
        ProxyConfig proxyConfig = ProxyConfig.fromUrl(
            Config.load().getProxyUrl()
        );

        // Step 1: Create initial client (for API key derivation)
        log.info("Step 1: Creating client and deriving API key...");
        PolymarketClient initialClient = new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(137) // Polygon mainnet
            //            .proxy(proxyConfig)
            .useServerTime(true) // Use server time to avoid clock skew issues
            .build();

        log.info("Wallet address: {}", initialClient.getAddress());

        // Derive or create API key
        ApiKeyCreds apiCreds = initialClient.createOrDeriveApiKey();
        log.info("API credentials obtained: {}", apiCreds);

        // Step 2: Create full client with API credentials
        log.info("\nStep 2: Creating trading client with API credentials...");
        PolymarketClient.Builder clientBuilder = new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(137)
            //            .proxy(proxyConfig)
            .useServerTime(true) // Use server time to avoid clock skew issues
            .apiCreds(apiCreds);

        if (funderAddress != null && !funderAddress.isEmpty()) {
            clientBuilder.funderAddress(funderAddress);
        }

        PolymarketClient client = clientBuilder.build();
        log.info(
            "Trading client created. Funder address: {}",
            client.getFunderAddress() != null
                ? client.getFunderAddress()
                : "(not set)"
        );

        // Step 3: Get server time
        log.info("\nStep 3: Getting server time...");
        long serverTime = client.getServerTime();
        log.info(
            "Server time: {} ({})",
            serverTime,
            java.time.Instant.ofEpochSecond(serverTime)
        );

        // Step 4: Fetch markets from CLOB API
        log.info("\nStep 4: Fetching markets from CLOB API...");
        Map<String, Object> marketsResponse = client.getMarkets(null);
        log.info("Markets response keys: {}", marketsResponse.keySet());
        Object data = marketsResponse.get("data");
        if (data instanceof java.util.List<?> list) {
            log.info("Found {} markets", list.size());
            if (!list.isEmpty()) {
                log.info("First market sample: {}", list.get(0));
            }
        }

        // Step 5: Get market data for a specific token (if we have one)
        log.info("\nStep 5: Getting sample market data...");
        // Use a sample token ID - in real usage you'd get this from market data
        String sampleTokenId = "";

        try {
            BigDecimal buyPrice = client.getPrice(sampleTokenId, "BUY");
            BigDecimal sellPrice = client.getPrice(sampleTokenId, "SELL");
            log.info("Sample token BUY price: {}", buyPrice);
            log.info("Sample token SELL price: {}", sellPrice);

            SpreadResult spread = client.getSpread(
                sampleTokenId
            );
            log.info(
                "Spread: {}",
                spread.getSpread()
            );

            String tickSize = client.getTickSize(sampleTokenId);
            log.info("Tick size: {}", tickSize);

            int feeRateBps = client.getFeeRateBps(sampleTokenId);
            log.info("Fee rate: {} bps ({}%)", feeRateBps, feeRateBps / 100.0);
        } catch (Exception e) {
            log.warn(
                "Could not get sample market data (token may not exist): {}",
                e.getMessage()
            );
        }

        // Step 6: Demo order creation (not posting)
        log.info("\nStep 6: Demonstrating order creation (not posting)...");
        String normalizedKey = privateKey.startsWith("0x")
            ? privateKey.substring(2)
            : privateKey;
        Credentials creds = Credentials.create(normalizedKey);
        OrderBuilder orderBuilder = new OrderBuilder(creds, 137);

        log.info("Order maker address: {}", orderBuilder.getMakerAddress());
        log.info("Order signer address: {}", orderBuilder.getSignerAddress());

        // Create a sample order payload (not posting)
        try {
            UserOrder userOrder = UserOrder.builder()
                .tokenID(sampleTokenId)
                .side(Side.BUY)
                .price(new BigDecimal("0.50"))
                .size(new BigDecimal("10"))
                .build();

            CreateOrderOptions options = CreateOrderOptions.builder()
                .tickSize("0.01")
                .negRisk(false)
                .build();

            SignedOrder signedOrder = orderBuilder.buildOrder(
                userOrder,
                options
            );

            PostOrderPayload payload = orderBuilder.buildPayload(
                signedOrder,
                apiCreds.getKey(),
                OrderType.GTC,
                false,
                false
            );

            log.info("Sample order payload created:");
            log.info("  Order type: {}", payload.orderType());
            log.info("  Owner: {}", payload.owner());

            log.info("  Token ID: {}", signedOrder.tokenId());
            log.info("  Side: {}", signedOrder.side());
            log.info("  Maker Amount: {}", signedOrder.makerAmount());
            log.info("  Taker Amount: {}", signedOrder.takerAmount());
            String signature = signedOrder.signature();
            log.info(
                "  Signature: {}...",
                signature.substring(0, Math.min(20, signature.length()))
            );
        } catch (Exception e) {
            log.warn("Could not create sample order: {}", e.getMessage());
        }

        // Step 7: Get open orders
        log.info("\nStep 7: Getting open orders...");
        try {
            var openOrders = client.getOpenOrders();
            log.info("Open orders count: {}", openOrders.size());
            if (!openOrders.isEmpty()) {
                log.info("First open order: {}", openOrders.get(0));
            }
        } catch (Exception e) {
            log.warn("Could not get open orders: {}", e.getMessage());
        }

        log.info("\n=== Example Complete ===");
        log.info("The client is ready for trading. To place real orders:");
        log.info(
            "1. Get market token IDs from getMarkets() or getGammaMarkets()"
        );
        log.info("2. Create an order with OrderBuilder.createOrder()");
        log.info("3. Post the order with client.postOrder(orderPayload)");
        log.info("4. Check order status with client.getOrder(orderId)");
        log.info("5. Cancel if needed with client.cancelOrder(orderId)");
    }
}
