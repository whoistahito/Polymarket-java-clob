package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.BanStatus;
import com.polymarket.model.BookParams;
import com.polymarket.model.HeartbeatResponse;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.OpenOrderParams;
import com.polymarket.model.OrderBookSummary;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderScoring;
import com.polymarket.model.OrderStatusType;
import com.polymarket.model.OrderSummary;
import com.polymarket.model.OrderType;
import com.polymarket.model.PaginationPayload;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.Side;
import com.polymarket.model.SignatureType;
import com.polymarket.model.SignedOrder;
import com.polymarket.model.Trade;
import com.polymarket.model.TradeParams;
import com.polymarket.model.TradeStatusType;
import com.polymarket.model.TraderSide;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AsyncPolymarketClient} — Phase 11 Milestone 5.
 *
 * <p>Real Polymarket data used throughout:
 * <ul>
 *   <li>Token: {@code 65818619657568813474341868652308942079804919287380422192892211131408793125422}
 *   <li>Market: {@code 0xbd31dc8a20211944f6b70f31557f1001557b59905b7738480ca09bd4532f84af}
 *   <li>Order-book taken from websocket.md orderbook snapshot example
 * </ul>
 */
@DisplayName("AsyncPolymarketClient Tests")
class AsyncPolymarketClientTest {

    // -----------------------------------------------------------------------
    // Real Polymarket test data (from docs/websocket.md)
    // -----------------------------------------------------------------------
    static final String TOKEN_ID =
        "65818619657568813474341868652308942079804919287380422192892211131408793125422";
    static final String MARKET =
        "0xbd31dc8a20211944f6b70f31557f1001557b59905b7738480ca09bd4532f84af";
    static final String CONDITION_ID =
        "0xbd31dc8a20211944f6b70f31557f1001557b59905b7738480ca09bd4532f84af";

    // Well-known test private key (hardhat account #0)
    static final String PK = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    static final String FUNDER = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    // -----------------------------------------------------------------------
    // Fixtures — pre-built real model instances
    // -----------------------------------------------------------------------

    /** Order-book snapshot matching the websocket.md example. */
    static final OrderBookSummary BOOK = OrderBookSummary.builder()
        .market(MARKET)
        .assetId(TOKEN_ID)
        .timestamp("1757908892351")
        .bids(List.of(
            OrderSummary.builder().price("0.48").size("30").build(),
            OrderSummary.builder().price("0.49").size("20").build(),
            OrderSummary.builder().price("0.50").size("15").build()
        ))
        .asks(List.of(
            OrderSummary.builder().price("0.52").size("25").build(),
            OrderSummary.builder().price("0.53").size("60").build(),
            OrderSummary.builder().price("0.54").size("10").build()
        ))
        .tickSize("0.01")
        .negRisk(false)
        .hash("0xabc123")
        .build();

    /** Realistic open order. */
    static final OpenOrder OPEN_ORDER = OpenOrder.builder()
        .id("0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b")
        .status(OrderStatusType.LIVE)
        .owner("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        .makerAddress("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        .market(MARKET)
        .assetId(TOKEN_ID)
        .side("BUY")
        .originalSize("50")
        .sizeMatched("0")
        .price("0.49")
        .associateTrades(Collections.emptyList())
        .outcome("Yes")
        .createdAt(1757908892351L)
        .expiration("0")
        .orderType("GTC")
        .build();

    /** Realistic filled trade. */
    static final Trade TRADE = Trade.builder()
        .id("0xaabb1122334455667788990011223344556677889900aabbccddeeff0011223344")
        .takerOrderId("0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b")
        .market(MARKET)
        .assetId(TOKEN_ID)
        .side(Side.BUY)
        .size("50")
        .feeRateBps("0")
        .price("0.49")
        .status(TradeStatusType.MATCHED)
        .matchTime("1757908892351")
        .lastUpdate("1757908892351")
        .outcome("Yes")
        .bucketIndex(0)
        .owner("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        .makerAddress("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        .makerOrders(Collections.emptyList())
        .transactionHash("0xdeadbeef")
        .traderSide(TraderSide.TAKER)
        .build();

    /** Realistic POST /order success response. */
    static final OrderResponse ORDER_RESPONSE = OrderResponse.builder()
        .success(true)
        .errorMsg("")
        .orderID("0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b")
        .transactionsHashes(Collections.emptyList())
        .status("matched")
        .takingAmount("24500000")
        .makingAmount("50000000")
        .build();

    /** Heartbeat response. */
    static final HeartbeatResponse HEARTBEAT =
        new HeartbeatResponse("hb-test-001", "ok");

    /** Order scoring result. */
    static final OrderScoring ORDER_SCORING = OrderScoring.builder().scoring(true).build();

    /** Ban status. */
    static final BanStatus BAN_STATUS = BanStatus.builder().closedOnly(false).build();

    /** Balance/allowance response. */
    static final BalanceAllowanceResponse BALANCE = BalanceAllowanceResponse.builder()
        .balance("1000000000")
        .allowance("999999999")
        .build();

    // -----------------------------------------------------------------------
    // Infrastructure
    // -----------------------------------------------------------------------

    private MockWebServer server;
    private PolymarketClient syncClient;
    private PolymarketClient syncClientL2;
    private PolymarketClient syncClientNoAuth;
    private AsyncPolymarketClient async;

    /** Direct executor so all futures complete on the calling thread — no concurrency in tests. */
    private static final Executor DIRECT = Runnable::run;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();

        syncClient = new PolymarketClient.Builder()
            .privateKey(PK)
            .clobHost(base)
            .gammaHost(base)
            .build();

        ApiKeyCreds creds = new ApiKeyCreds("test-api-key", "c2VjcmV0", "passphrase123");
        syncClientL2 = new PolymarketClient.Builder()
            .privateKey(PK)
            .funderAddress(FUNDER)
            .apiCreds(creds)
            .clobHost(base)
            .gammaHost(base)
            .build();

        syncClientNoAuth = new PolymarketClient.Builder()
            .privateKey(PK)
            .clobHost(base)
            .gammaHost(base)
            .build();

        async = AsyncPolymarketClient.wrap(syncClientL2, DIRECT);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json"));
    }

    // -----------------------------------------------------------------------
    // TC-AC-001 — Factory / Builder
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Factory / Builder")
    class Factory {

        @Test
        @DisplayName("TC-AC-001: wrap(client) uses ForkJoinPool common pool")
        void wrapDefaultExecutor() {
            AsyncPolymarketClient a = AsyncPolymarketClient.wrap(syncClient);
            assertNotNull(a);
            assertSame(syncClient, a.syncClient());
            assertNotNull(a.getExecutor());
        }

        @Test
        @DisplayName("TC-AC-002: wrap(client, executor) stores provided executor")
        void wrapCustomExecutor() {
            Executor custom = Executors.newSingleThreadExecutor();
            AsyncPolymarketClient a = AsyncPolymarketClient.wrap(syncClient, custom);
            assertSame(custom, a.getExecutor());
            assertSame(syncClient, a.syncClient());
        }

        @Test
        @DisplayName("TC-AC-003: wrap(null) throws NullPointerException")
        void wrapNullClientThrows() {
            assertThrows(NullPointerException.class,
                () -> AsyncPolymarketClient.wrap(null));
        }

        @Test
        @DisplayName("TC-AC-004: wrap(client, null executor) throws NullPointerException")
        void wrapNullExecutorThrows() {
            assertThrows(NullPointerException.class,
                () -> AsyncPolymarketClient.wrap(syncClient, null));
        }
    }

    // -----------------------------------------------------------------------
    // TC-AC-010 — Synchronous pass-through delegates (no I/O, no MockWebServer)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Synchronous delegates")
    class SyncDelegates {

        @Test
        @DisplayName("TC-AC-010: getAddress() returns wallet address")
        void getAddress() {
            // Well-known address for the test private key (hardhat #0)
            assertEquals("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                async.getAddress().toLowerCase());
        }

        @Test
        @DisplayName("TC-AC-011: getChainId() returns 137 (Polygon Mainnet default)")
        void getChainId() {
            assertEquals(137, async.getChainId());
        }

        @Test
        @DisplayName("TC-AC-012: hasApiCreds() returns true when creds present")
        void hasApiCreds() {
            assertTrue(async.hasApiCreds());
        }

        @Test
        @DisplayName("TC-AC-013: hasApiCreds() returns false when no creds")
        void hasApiCredsNoAuth() {
            AsyncPolymarketClient noAuth = AsyncPolymarketClient.wrap(syncClientNoAuth, DIRECT);
            assertFalse(noAuth.hasApiCreds());
        }

        @Test
        @DisplayName("TC-AC-014: clearTickSizeCache() executes without error")
        void clearTickSizeCache() {
            assertDoesNotThrow(() -> async.clearTickSizeCache());
        }

        @Test
        @DisplayName("TC-AC-015: clearTickSizeCache(tokenId) executes without error")
        void clearTickSizeCacheByToken() {
            assertDoesNotThrow(() -> async.clearTickSizeCache(TOKEN_ID));
        }

        @Test
        @DisplayName("TC-AC-016: getOrderBookHash() delegates to PriceUtils (no I/O)")
        void getOrderBookHash() {
            // BOOK fixture has real bids/asks — hash should be stable
            String hash = async.getOrderBookHash(BOOK);
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
            // Hash must be deterministic: same input → same output
            assertEquals(hash, async.getOrderBookHash(BOOK));
        }

        @Test
        @DisplayName("TC-AC-017: calculateMarketPrice() walks bids/asks (no I/O)")
        void calculateMarketPrice() {
            // BUY: walk asks from most expensive (0.54@10) toward cheapest (0.52@25)
            // Matches TS SDK algorithm: sum cost from most-expensive level first
            // i=2: 0.54, 10*0.54=5.4 < 12 → continue
            // i=1: 0.53, 5.4 + 60*0.53=37.2 >= 12 → return 0.53
            BigDecimal price = async.calculateMarketPrice(
                Side.BUY, new BigDecimal("12"), OrderType.FAK, BOOK);
            assertEquals(new BigDecimal("0.53"), price);
        }
    }

    // -----------------------------------------------------------------------
    // TC-AC-020 — Successful async operations via MockWebServer
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Successful async futures (MockWebServer)")
    class AsyncSuccess {

        @Test
        @DisplayName("TC-AC-020: getServerTime() parses bare integer response")
        void getServerTime() throws Exception {
            enqueue("1757908892");
            assertEquals(1757908892L, async.getServerTime().get());
        }

        @Test
        @DisplayName("TC-AC-021: getOrderBook() deserialises bids and asks")
        void getOrderBook() throws Exception {
            enqueue("""
                {
                  "market": "%s",
                  "asset_id": "%s",
                  "timestamp": "1757908892351",
                  "bids": [{"price":"0.48","size":"30"},{"price":"0.49","size":"20"}],
                  "asks": [{"price":"0.52","size":"25"},{"price":"0.53","size":"60"}],
                  "tick_size": "0.01",
                  "neg_risk": false
                }""".formatted(MARKET, TOKEN_ID));

            OrderBookSummary book = async.getOrderBook(TOKEN_ID).get();

            assertEquals(TOKEN_ID, book.getAssetId());
            assertEquals(2, book.getBids().size());
            assertEquals("0.48", book.getBids().get(0).getPrice());
            assertEquals(2, book.getAsks().size());
            assertEquals("0.52", book.getAsks().get(0).getPrice());
        }

        @Test
        @DisplayName("TC-AC-022: getTickSize() parses tick_size field")
        void getTickSize() throws Exception {
            enqueue("{\"tick_size\":\"0.01\"}");
            assertEquals("0.01", async.getTickSize(TOKEN_ID).get());
        }

        @Test
        @DisplayName("TC-AC-023: getMidpoint() parses mid field")
        void getMidpoint() throws Exception {
            enqueue("{\"mid\":\"0.51\"}");
            assertEquals(new BigDecimal("0.51"), async.getMidpoint(TOKEN_ID).get());
        }

        @Test
        @DisplayName("TC-AC-024: getOpenOrders() returns list of OpenOrder")
        void getOpenOrders() throws Exception {
            enqueue("""
                [{
                  "id": "0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b",
                  "status": "LIVE",
                  "owner": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                  "maker_address": "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                  "market": "%s",
                  "asset_id": "%s",
                  "side": "BUY",
                  "original_size": "50",
                  "size_matched": "0",
                  "price": "0.49",
                  "outcome": "Yes",
                  "created_at": 1757908892351,
                  "expiration": "0",
                  "order_type": "GTC"
                }]""".formatted(MARKET, TOKEN_ID));

            List<OpenOrder> orders = async.getOpenOrders().get();
            assertEquals(1, orders.size());
            OpenOrder o = orders.get(0);
            assertEquals("0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b", o.getId());
            assertEquals(OrderStatusType.LIVE, o.getStatus());
            assertEquals("0.49", o.getPrice());
            assertEquals(TOKEN_ID, o.getAssetId());
        }

        @Test
        @DisplayName("TC-AC-025: getOpenOrders(OpenOrderParams) sends asset_id query param")
        void getOpenOrdersWithParams() throws Exception {
            enqueue("[]");
            OpenOrderParams params = OpenOrderParams.builder().assetId(TOKEN_ID).build();
            List<OpenOrder> orders = async.getOpenOrders(params).get();
            assertTrue(orders.isEmpty());
            RecordedRequest req = server.takeRequest();
            assertTrue(req.getPath().contains("asset_id=" + TOKEN_ID),
                "Expected asset_id in path: " + req.getPath());
        }

        @Test
        @DisplayName("TC-AC-026: postOrder(PostOrderPayload) returns OrderResponse")
        void postOrder() throws Exception {
            enqueue("""
                {
                  "success": true,
                  "errorMsg": "",
                  "orderID": "0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b",
                  "transactionsHashes": [],
                  "status": "matched",
                  "takingAmount": "24500000",
                  "makingAmount": "50000000"
                }""");

            SignedOrder signed = SignedOrder.builder()
                .salt(1234567890123456789L)
                .maker(FUNDER)
                .signer(FUNDER)
                .taker("0x0000000000000000000000000000000000000000")
                .tokenId(TOKEN_ID)
                .makerAmount("50000000")
                .takerAmount("24500000")
                .expiration("0")
                .nonce("0")
                .feeRateBps("0")
                .signatureType(SignatureType.EOA)
                .signature("0xdeadbeef")
                .build();

            PostOrderPayload payload = PostOrderPayload.builder()
                .order(signed)
                .owner(FUNDER)
                .orderType(OrderType.GTC)
                .build();

            OrderResponse resp = async.postOrder(payload).get();
            assertTrue(resp.success());
            assertEquals("0x7f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b", resp.orderID());
        }

        @Test
        @DisplayName("TC-AC-027: cancelAll() returns response map with canceled key")
        void cancelAll() throws Exception {
            enqueue("{\"canceled\":[\"0x7f1b2c3d\"]}");
            Map<String, Object> result = async.cancelAll().get();
            assertNotNull(result.get("canceled"));
        }

        @Test
        @DisplayName("TC-AC-028: postHeartbeat() returns HeartbeatResponse with heartbeat_id")
        void postHeartbeat() throws Exception {
            enqueue("{\"heartbeat_id\":\"hb-test-001\",\"status\":\"ok\"}");
            HeartbeatResponse hb = async.postHeartbeat("hb-test-001").get();
            assertEquals("hb-test-001", hb.getHeartbeatId());
        }

        @Test
        @DisplayName("TC-AC-029: isOrderScoring() returns OrderScoring.scoring = true")
        void isOrderScoring() throws Exception {
            enqueue("{\"scoring\":true}");
            OrderScoring s = async.isOrderScoring("0x7f1b2c3d").get();
            assertTrue(s.isScoring());
        }

        @Test
        @DisplayName("TC-AC-030: getBalanceAllowance() parses balance and allowance")
        void getBalanceAllowance() throws Exception {
            enqueue("{\"balance\":\"1000000000\",\"allowance\":\"999999999\"}");
            BalanceAllowanceResponse resp = async
                .getBalanceAllowance(BalanceAllowanceParams.builder().build()).get();
            assertEquals("1000000000", resp.getBalance());
            assertEquals("999999999", resp.getAllowance());
        }

        @Test
        @DisplayName("TC-AC-031: getClosedOnlyMode() parses closed_only = false")
        void getClosedOnlyMode() throws Exception {
            enqueue("{\"closed_only\":false}");
            BanStatus status = async.getClosedOnlyMode().get();
            assertFalse(status.getClosedOnly());
        }
    }

    // -----------------------------------------------------------------------
    // TC-AC-040 — Void async futures
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Void async futures")
    class AsyncVoid {

        @Test
        @DisplayName("TC-AC-040: updateBalanceAllowance() completes with null result")
        void updateBalanceAllowance() throws Exception {
            enqueue("{\"success\":true}");
            CompletableFuture<Void> f =
                async.updateBalanceAllowance(BalanceAllowanceParams.builder().build());
            assertNull(f.get());
        }
    }

    // -----------------------------------------------------------------------
    // TC-AC-050 — Exception propagation
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Exception propagation")
    class Exceptions {

        @Test
        @DisplayName("TC-AC-050: IOException (connection refused) wraps as UncheckedIOException")
        void ioExceptionWrapped() throws Exception {
            // Shut down the server so the next HTTP call gets a connection-refused IOException
            server.shutdown();

            CompletableFuture<Long> future = async.getServerTime();
            assertTrue(future.isCompletedExceptionally());
            CompletionException ce = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(UncheckedIOException.class, ce.getCause());
        }

        @Test
        @DisplayName("TC-AC-051: Missing L2 auth propagates IllegalStateException")
        void missingL2AuthPropagates() {
            AsyncPolymarketClient noAuth = AsyncPolymarketClient.wrap(syncClientNoAuth, DIRECT);

            CompletableFuture<List<OpenOrder>> future = noAuth.getOpenOrders();
            assertTrue(future.isCompletedExceptionally());

            CompletionException ce = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(IllegalStateException.class, ce.getCause());
            assertTrue(ce.getCause().getMessage().contains("credentials"),
                "Expected auth error message, got: " + ce.getCause().getMessage());
        }

        @Test
        @DisplayName("TC-AC-052: cancelAll() without L2 auth propagates IllegalStateException")
        void cancelAllWithoutAuthPropagates() {
            AsyncPolymarketClient noAuth = AsyncPolymarketClient.wrap(syncClientNoAuth, DIRECT);
            CompletableFuture<Map<String, Object>> future = noAuth.cancelAll();
            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, future::join);
        }

        @Test
        @DisplayName("TC-AC-053: Future is already completed (not pending) on DIRECT executor")
        void futureImmediatelyDoneOnDirectExecutor() {
            // Shut down so the call fails immediately — future should be done before we even check
            try { server.shutdown(); } catch (IOException ignored) {}
            CompletableFuture<Long> f = async.getServerTime();
            assertTrue(f.isDone(), "Future must be completed when using DIRECT executor");
        }
    }

    // -----------------------------------------------------------------------
    // TC-AC-060 — Executor behaviour
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Executor behaviour")
    class ExecutorBehaviour {

        @Test
        @DisplayName("TC-AC-060: Task runs on provided executor thread")
        void taskRunsOnProvidedExecutor() throws Exception {
            AtomicReference<String> threadName = new AtomicReference<>();
            Executor captureExecutor = r -> {
                threadName.set(Thread.currentThread().getName());
                r.run();
            };
            AsyncPolymarketClient a = AsyncPolymarketClient.wrap(syncClientL2, captureExecutor);

            enqueue("1757908892");
            a.getServerTime().get();

            // threadName is set to whichever thread ran the executor
            assertNotNull(threadName.get());
        }
    }

    // -----------------------------------------------------------------------
    // TC-AC-070 — rfq() sub-client
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("rfq() sub-client")
    class RfqSubClient {

        @Test
        @DisplayName("TC-AC-070: rfq() returns AsyncRfqClient instance")
        void rfqReturnsAsyncRfqClient() {
            assertInstanceOf(AsyncRfqClient.class, async.rfq());
        }

        @Test
        @DisplayName("TC-AC-071: rfq() returns new instance each call")
        void rfqNewInstanceEachCall() {
            assertNotSame(async.rfq(), async.rfq());
        }
    }
}
