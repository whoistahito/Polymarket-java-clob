package com.polymarket.integration;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.PolymarketClient;
import com.polymarket.integration.LiveTestSupport.*;
import com.polymarket.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live end-to-end smoke test against the <b>real</b> Polymarket CLOB.
 *
 * <p>Run it by hand after a refactor to confirm the SDK still behaves against production. It is
 * <b>disabled unless {@code POLYMARKET_LIVE=1}</b>, so a normal {@code mvn test} never touches the
 * network. You only supply a private key; {@link LiveTestSupport} discovers a deposit wallet and a
 * market to canary on. See {@link LiveTestSupport} for the domain model and configuration knobs.
 *
 * <p>{@link #bootstrap()} authenticates and resolves the wallet + market once; the numbered checks
 * then exercise a facet each. All but {@link #orderLifecycle()} are read-only. The write path posts
 * a <b>GTC buy at the minimum tick price</b>, marked <b>post-only</b>, capped at {@code
 * POLYMARKET_MAX_SPEND} — three independent rails ensure it can never fill (tick price far below
 * market, a pre-submit {@code price < bestAsk} assertion, and exchange-side post-only rejection).
 * It is cancelled by <i>its own id</i> in a {@code finally} block; real orders are never touched.
 */
@DisplayName("Live CLOB smoke test (opt-in)")
@EnabledIfEnvironmentVariable(named = "POLYMARKET_LIVE", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveSmokeTest {

    private static EnvConfig cfg;
    private static ApiKeyCreds creds;
    private static PolymarketClient probe; // EOA-signer client, for auth/balance diagnostics
    private static DepositWallet deposit;
    private static PolymarketClient client; // trades on behalf of the deposit wallet
    private static CanaryMarket market;

    @BeforeAll
    static void bootstrap() throws Exception {
        cfg = EnvConfig.fromEnv();
        assumeTrue(cfg.hasPrivateKey(), "set POLYMARKET_PRIVATE_KEY");

        // L2 creds bound to the EOA signer, independent of funder.
        creds = clientBuilder(cfg, SignatureType.EOA, null).build().createOrDeriveApiKey();
        assertNotNull(creds, "createOrDeriveApiKey returned null");
        probe = clientBuilder(cfg, SignatureType.EOA, null).apiCreds(creds).build();
        log("L2 creds ok for signer %s", redact(probe.getAddress()));

        // Polymarket rejects bare-EOA makers, so trades go through the proxy/Safe deposit wallet.
        deposit = resolveDeposit(probe, cfg);
        assumeTrue(deposit != null,
                "no funded Polymarket deposit wallet found for this key — fund your proxy/Safe with a "
                        + "little USDC, or set POLYMARKET_SIG_TYPE + POLYMARKET_FUNDER explicitly");
        log("using deposit wallet %s (%s)", redact(deposit.funder()), deposit.sigType());

        client = clientBuilder(cfg, deposit.sigType(), deposit.funder()).apiCreds(creds).build();

        market = discoverCanaryMarket(client, cfg);
        assumeTrue(market != null,
                "no suitable market found — widen POLYMARKET_END_WITHIN_HOURS or set POLYMARKET_TOKEN_ID");
        log("using token %s tickSize=%s negRisk=%s",
                redact(market.tokenId()), market.tickSize(), market.negRisk());
    }

    @Test
    @Order(1)
    @DisplayName("connectivity + server clock")
    void connectivityAndClock() throws Exception {
        assertTrue(client.getServerTime() > 0, "server time not positive");
    }

    @Test
    @Order(2)
    @DisplayName("API-key derivation is deterministic (L1 signing stable)")
    void apiKeyDerivationIsDeterministic() throws Exception {
        // Same key + same nonce must re-derive byte-identical creds, or L1 signing is non-deterministic.
        ApiKeyCreds again = clientBuilder(cfg, SignatureType.EOA, null).build().deriveApiKey();
        assertTrue(sameCreds(creds, again), "deriveApiKey not deterministic for the same key");
    }

    @Test
    @Order(3)
    @DisplayName("market metadata is coherent")
    void marketMetadataIsCoherent() {
        assertTrue(
                List.of("0.1", "0.01", "0.001", "0.0001").contains(market.tickSize()),
                "unexpected tick size " + market.tickSize());
        OrderBookSummary book = market.book();
        assertNotNull(book, "order book null");
        assertNotNull(book.getHash(), "order book has no hash");
        assertTrue(
                !nz(book.getBids()).isEmpty() || !nz(book.getAsks()).isEmpty(),
                "order book has neither bids nor asks");
    }

    @Test
    @Order(4)
    @DisplayName("pricing endpoints agree with the book")
    void pricingEndpointsAgree() throws Exception {
        String tokenId = market.tokenId();
        BigDecimal mid = client.getMidpoint(tokenId);
        SpreadResult spread = client.getSpread(tokenId);
        assertNotNull(mid, "midpoint null");
        assertTrue(mid.signum() > 0 && mid.compareTo(BigDecimal.ONE) < 0, "midpoint outside (0,1): " + mid);
        assertNotNull(spread.getSpread(), "spread null");
        assertTrue(spread.getSpread().signum() >= 0, "negative spread: " + spread.getSpread());

        // Midpoint must sit between best bid and best ask (one-tick tolerance absorbs book drift
        // between the separate REST calls).
        BigDecimal bestBid = bestBidPrice(market.book().getBids());
        BigDecimal bestAsk = bestAskPrice(market.book().getAsks());
        if (bestBid != null && bestAsk != null) {
            BigDecimal tick = new BigDecimal(market.tickSize());
            assertTrue(mid.compareTo(bestBid.subtract(tick)) >= 0
                            && mid.compareTo(bestAsk.add(tick)) <= 0,
                    "midpoint " + mid + " not within [" + bestBid + ", " + bestAsk + "]");
        }
    }

    @Test
    @Order(5)
    @DisplayName("open orders deserialise from the live paginated envelope")
    void openOrdersReturnsList() throws Exception {
        // Regression guard: the live /orders endpoint returns {limit,next_cursor,count,data},
        // not a bare array. A null here means the envelope parsing broke.
        assertNotNull(client.getOpenOrders(), "getOpenOrders returned null");
    }

    @Test
    @Order(6)
    @DisplayName("deposit wallet balance is queryable")
    void depositWalletBalanceIsQueryable() {
        BigDecimal bal = usdcBalance(probe, deposit);
        assertNotNull(bal, "USDC balance not queryable for deposit wallet");
        assertTrue(bal.signum() >= 0, "negative balance: " + bal);
    }

    @Test
    @Order(7)
    @DisplayName("place a non-fillable GTC order, see it resting, then cancel it")
    void orderLifecycle() throws Exception {
        OrderBookSummary book = market.book();
        BigDecimal price = new BigDecimal(market.tickSize()); // lowest possible price
        BigDecimal bestAsk = bestAskPrice(book.getAsks());
        // A buy fills only if price >= bestAsk. Refuse to place anything that could cross.
        assertTrue(
                bestAsk == null || price.compareTo(bestAsk) < 0,
                "price " + price + " would cross bestAsk " + bestAsk
                        + " — pick a market not trading at the minimum tick");

        BigDecimal size = new BigDecimal(
                firstNonBlank(cfg.testSizeOverride(), firstNonBlank(book.getMinOrderSize(), "5")));
        BigDecimal notional = price.multiply(size);
        assertTrue(
                notional.compareTo(cfg.maxSpend()) <= 0,
                "notional " + notional + " exceeds POLYMARKET_MAX_SPEND " + cfg.maxSpend());
        log("will rest BUY size=%s @ price=%s (notional=%s, cap=%s)", size, price, notional, cfg.maxSpend());

        String createdOrderId = null;
        try {
            SignedOrder signed = client.createOrder(
                    UserOrder.builder()
                            .tokenID(market.tokenId())
                            .price(price)
                            .size(size)
                            .side(Side.BUY)
                            .build(),
                    CreateOrderOptions.builder().tickSize(market.tickSize()).negRisk(market.negRisk()).build());
            assertNotNull(signed, "createOrder returned null");
            log("signed order: maker=%s signer=%s sigType=%s",
                    redact(signed.maker()), redact(signed.signer()), signed.signatureType());

            // GTC + postOnly: a resting maker order the exchange rejects if it would ever cross.
            OrderResponse resp = client.postOrder(signed, OrderType.GTC, true, false);
            assertNotNull(resp, "postOrder returned null");
            assertTrue(resp.success(), "postOrder failed: " + resp.errorMsg());
            createdOrderId = resp.orderID();
            assertNotNull(createdOrderId, "no orderID in response");
            log("posted order %s (status=%s)", createdOrderId, resp.status());

            assertTrue(
                    containsOrder(client.getOpenOrders(), createdOrderId),
                    "posted order not found in open orders");

            client.cancelOrder(createdOrderId);
            log("cancelled order %s", createdOrderId);

            assertTrue(
                    !containsOrder(client.getOpenOrders(), createdOrderId),
                    "order still open after cancel");
            createdOrderId = null; // cleaned up
        } finally {
            if (createdOrderId != null) {
                try {
                    client.cancelOrder(createdOrderId);
                    log("cleanup cancelled leftover order %s", createdOrderId);
                } catch (Exception e) {
                    log("CLEANUP FAILED for %s — cancel it manually: %s", createdOrderId, e.getMessage());
                }
            }
        }
    }
}
