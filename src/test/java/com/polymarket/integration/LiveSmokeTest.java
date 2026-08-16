package com.polymarket.integration;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.HttpStatusException;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.*;
import com.polymarket.ws.WsClient;
import com.polymarket.ws.WsMessageListener;
import com.polymarket.ws.model.WsMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.polymarket.integration.LiveTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
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
 * then exercise a facet each. Every write goes through {@link LiveTestSupport#signNonCrossingBuy}, a
 * BUY at the minimum tick that <b>provably cannot fill</b> (three rails: tick price far below
 * market, a pre-submit {@code price < bestAsk} check, and — for resting orders — exchange-side
 * post-only rejection). Order sizes come from the market's {@code orderMinSize}, bounded by a hard
 * {@code POLYMARKET_MAX_SPEND} notional cap. Every order is cancelled by <i>its own id</i>; real
 * orders on the account are never touched. No order ever crosses the book, so none can fill.
 */
@DisplayName("Live CLOB smoke test (opt-in)")
@Tag("live")
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

    // ---------------------------------------------------------------------------------------------
    // Read-only checks
    // ---------------------------------------------------------------------------------------------

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
    @DisplayName("trade history reads (list, possibly empty)")
    void tradeHistoryReads() throws Exception {
        List<Trade> trades = client.getTrades();
        assertNotNull(trades, "getTrades returned null");
        log("account has %d trade(s)", trades.size());
    }

    // ---------------------------------------------------------------------------------------------
    // Write path — every order is provably non-fillable and cleaned up
    // ---------------------------------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("GTC: place a non-fillable order, see it resting, then cancel it")
    void orderLifecycle() throws Exception {
        SignedOrder signed = signNonCrossingBuy(client, market, cfg, null, resolveSize(market, cfg));
        log("signed order: maker=%s signer=%s sigType=%s",
                redact(signed.maker()), redact(signed.signer()), signed.signatureType());
        String id = null;
        try {
            OrderResponse resp = client.postOrder(signed, OrderType.GTC, true, false);
            assertTrue(resp.success(), "postOrder failed: " + resp.errorMsg());
            id = resp.orderID();
            assertNotNull(id, "no orderID in response");
            log("posted order %s (status=%s)", id, resp.status());

            assertTrue(containsOrder(client.getOpenOrders(), id), "posted order not found in open orders");

            client.cancelOrder(id);
            assertTrue(!containsOrder(client.getOpenOrders(), id), "order still open after cancel");
            id = null; // cleaned up
        } finally {
            cleanup(id);
        }
    }

    @Test
    @Order(9)
    @DisplayName("GTC: single-order fetch by id round-trips")
    void getOrderByIdRoundTrips() throws Exception {
        SignedOrder signed = signNonCrossingBuy(client, market, cfg, null, resolveSize(market, cfg));
        String id = null;
        try {
            OrderResponse resp = client.postOrder(signed, OrderType.GTC, true, false);
            assertTrue(resp.success(), "postOrder failed: " + resp.errorMsg());
            id = resp.orderID();

            OpenOrder fetched = client.getOrder(id);
            assertNotNull(fetched, "getOrder returned null");
            assertEquals(id, fetched.getId(), "getOrder returned a different order");
            assertEquals(market.tokenId(), fetched.getAssetId(), "getOrder asset id mismatch");
        } finally {
            cleanup(id);
        }
    }

    @Test
    @Order(10)
    @DisplayName("GTD: order rests with an expiry, then cancel")
    void gtdOrderRestsThenCancel() throws Exception {
        long expiration = Instant.now().getEpochSecond() + 300; // 5 min out; cancelled well before
        SignedOrder signed = signNonCrossingBuy(client, market, cfg, expiration, resolveSize(market, cfg));
        String id = null;
        try {
            OrderResponse resp = client.postOrder(signed, OrderType.GTD, true, false);
            assertTrue(resp.success(), "GTD postOrder failed: " + resp.errorMsg());
            id = resp.orderID();
            assertTrue(containsOrder(client.getOpenOrders(), id), "GTD order not resting");

            client.cancelOrder(id);
            assertTrue(!containsOrder(client.getOpenOrders(), id), "GTD order still open after cancel");
            id = null;
        } finally {
            cleanup(id);
        }
    }

    @Test
    @Order(11)
    @DisplayName("FOK/FAK: marketable orders don't rest (killed or rejected), never fill")
    void marketableOrdersDoNotRest() throws Exception {
        // Sized at the market minimum (orderMinSize) and priced at the tick, so they cannot cross
        // and never fill. The exchange either kills them or rejects them (its own marketable-size
        // rule) — either way nothing rests. Exercises the FOK/FAK submit path without spending.
        for (OrderType type : List.of(OrderType.FOK, OrderType.FAK)) {
            SignedOrder signed = signNonCrossingBuy(client, market, cfg, null, resolveSize(market, cfg));
            postExpectingNoResting(signed, type, false, type + " non-crossing");
        }
    }

    @Test
    @Order(13)
    @DisplayName("batch cancel removes multiple resting orders in one call")
    void batchCancelRemovesAll() throws Exception {
        BigDecimal size = resolveSize(market, cfg);
        List<String> ids = new ArrayList<>();
        try {
            for (BigDecimal s : List.of(size, size.add(BigDecimal.ONE))) {
                SignedOrder signed = signNonCrossingBuy(client, market, cfg, null, s);
                OrderResponse resp = client.postOrder(signed, OrderType.GTC, true, false);
                assertTrue(resp.success(), "postOrder failed: " + resp.errorMsg());
                ids.add(resp.orderID());
            }
            client.cancelOrders(ids);

            List<OpenOrder> open = client.getOpenOrders();
            for (String id : ids) {
                assertTrue(!containsOrder(open, id), "order " + id + " still open after batch cancel");
            }
            ids.clear();
        } finally {
            if (!ids.isEmpty()) {
                try {
                    client.cancelOrders(ids);
                } catch (Exception e) {
                    log("CLEANUP FAILED for %s — cancel manually: %s", ids, e.getMessage());
                }
            }
        }
    }

    @Test
    @Order(14)
    @DisplayName("async wrapper runs the full order lifecycle")
    void asyncOrderLifecycle() throws Exception {
        AsyncPolymarketClient async = AsyncPolymarketClient.wrap(client);
        SignedOrder signed = signNonCrossingBuy(client, market, cfg, null, resolveSize(market, cfg));
        String id = null;
        try {
            OrderResponse resp = async.postOrder(signed, OrderType.GTC, true, false).get(30, TimeUnit.SECONDS);
            assertTrue(resp.success(), "async postOrder failed: " + resp.errorMsg());
            id = resp.orderID();
            assertTrue(
                    containsOrder(async.getOpenOrders().get(30, TimeUnit.SECONDS), id),
                    "async order not resting");

            async.cancelOrder(id).get(30, TimeUnit.SECONDS);
            assertTrue(
                    !containsOrder(async.getOpenOrders().get(30, TimeUnit.SECONDS), id),
                    "async order still open after cancel");
            id = null;
        } finally {
            if (id != null) {
                try {
                    async.cancelOrder(id).get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log("CLEANUP FAILED for %s — cancel manually: %s", id, e.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Live feed
    // ---------------------------------------------------------------------------------------------

    @Test
    @Order(15)
    @DisplayName("WS market channel streams a book snapshot")
    void wsMarketChannelStreamsBook() throws Exception {
        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<WsMessage> first = new AtomicReference<>();
        WsClient ws = WsClient.builder()
                .listener(new WsMessageListener() {
                    @Override
                    public void onMessage(WsMessage m) {
                        if (first.compareAndSet(null, m)) got.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        log("ws error: %s", e.getMessage());
                    }

                    @Override
                    public void onClose(int code, String reason) {
                    }
                })
                .build();
        try {
            ws.subscribeMarket(List.of(market.tokenId()));
            assertTrue(got.await(15, TimeUnit.SECONDS), "no WS message within 15s of subscribing");
            log("ws first message: %s", first.get().getClass().getSimpleName());
        } finally {
            ws.close();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Local helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Posts an order that must never rest — a marketable kill (FOK/FAK) or a post-only rejection.
     * Tolerates both server outcomes: an HTTP rejection (exception) or a non-resting response. Fails
     * only if the order actually rests, and cancels it if so.
     */
    private void postExpectingNoResting(SignedOrder signed, OrderType type, boolean postOnly, String label)
            throws Exception {
        try {
            OrderResponse resp = client.postOrder(signed, type, postOnly, false);
            log("%s -> success=%s status=%s err=%s", label, resp.success(), resp.status(), resp.errorMsg());
            String id = resp.orderID();
            if (id != null && containsOrder(client.getOpenOrders(), id)) {
                client.cancelOrder(id); // safety: should never happen for a kill/rejection
                fail(label + " unexpectedly rested (id=" + id + ")");
            }
        } catch (HttpStatusException e) {
            log("%s -> rejected by exchange: %s", label, e.getMessage()); // e.g. post-only cross
        }
    }

    /**
     * Best-effort cancel of a leftover order id in a finally block.
     */
    private void cleanup(String id) {
        if (id == null) return;
        try {
            client.cancelOrder(id);
            log("cleanup cancelled leftover order %s", id);
        } catch (Exception e) {
            log("CLEANUP FAILED for %s — cancel it manually: %s", id, e.getMessage());
        }
    }
}
