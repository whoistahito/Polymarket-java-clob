package com.polymarket.integration;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.AssetType;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.OrderBookSummary;
import com.polymarket.model.SpreadResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link AsyncPolymarketClient}.
 *
 * <p>All tests use {@link MockWebServer} and validate that the
 * {@code CompletableFuture}-based API correctly delegates to the underlying
 * synchronous client and handles results/errors properly.
 */
@DisplayName("AsyncPolymarketClient Integration Tests")
class AsyncPolymarketClientIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final ApiKeyCreds TEST_CREDS =
            new ApiKeyCreds("test-api-key-uuid", "c2VjcmV0MTIzNDU2Nzg=", "test-passphrase");
    private static final String TOKEN_ID =
            "71321045679252212594626385532706912750332728571942532289631379312455583992563";

    private MockWebServer server;
    private PolymarketClient syncClient;
    private AsyncPolymarketClient asyncClient;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        String mockBase = server.url("").toString().replaceAll("/$", "");
        syncClient = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .chainId(137)
                .clobHost(mockBase)
                .apiCreds(TEST_CREDS)
                .build();

        asyncClient = AsyncPolymarketClient.wrap(syncClient,
                Executors.newFixedThreadPool(4));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-301: getAddress / getChainId are synchronous pass-throughs
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-301: getAddress() and getChainId() delegate correctly without a future")
    void testSyncAccessors() {
        String address = asyncClient.getAddress();
        assertNotNull(address);
        assertTrue(address.startsWith("0x"));
        assertEquals(137, asyncClient.getChainId());
    }

    // -----------------------------------------------------------------------
    // TC-IT-302: getServerTime() returns a CompletableFuture<Long>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-302: getServerTime() completes the future with a long epoch-second")
    void testGetServerTimeAsync() throws Exception {
        enqueue("1700000000");

        CompletableFuture<Long> future = asyncClient.getServerTime();
        Long time = future.get();

        assertEquals(1700000000L, time);
    }

    // -----------------------------------------------------------------------
    // TC-IT-303: getOrderBook() returns CompletableFuture<OrderBookSummary>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-303: getOrderBook() completes with a correctly deserialised OrderBookSummary")
    void testGetOrderBookAsync() throws Exception {
        enqueue("""
                {
                  "market": "0xm1",
                  "asset_id": "%s",
                  "bids": [{"price": "0.45", "size": "100"}],
                  "asks": [{"price": "0.46", "size": "150"}],
                  "tick_size": "0.01",
                  "neg_risk": false
                }
                """.formatted(TOKEN_ID));

        OrderBookSummary book = asyncClient.getOrderBook(TOKEN_ID).get();

        assertNotNull(book);
        assertEquals(TOKEN_ID, book.getAssetId());
        assertEquals(1, book.getBids().size());
        assertEquals("0.45", book.getBids().get(0).getPrice());
    }

    // -----------------------------------------------------------------------
    // TC-IT-304: getMidpoint() returns CompletableFuture<BigDecimal>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-304: getMidpoint() completes with the parsed midpoint value")
    void testGetMidpointAsync() throws Exception {
        enqueue("{\"mid\": \"0.455\"}");

        BigDecimal mid = asyncClient.getMidpoint(TOKEN_ID).get();

        assertEquals(new BigDecimal("0.455"), mid);
    }

    // -----------------------------------------------------------------------
    // TC-IT-305: getSpread() returns CompletableFuture<SpreadResult>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-305: getSpread() completes with a SpreadResult")
    void testGetSpreadAsync() throws Exception {
        enqueue("{\"spread\": \"0.01\"}");

        SpreadResult spread = asyncClient.getSpread(TOKEN_ID).get();

        assertNotNull(spread);
        assertEquals(new BigDecimal("0.01"), spread.getSpread());
    }

    // -----------------------------------------------------------------------
    // TC-IT-306: getOpenOrders() returns CompletableFuture<List<OpenOrder>>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-306: getOpenOrders() completes with a list of OpenOrder")
    void testGetOpenOrdersAsync() throws Exception {
        enqueue("""
                [
                  {
                    "id": "0xORD1",
                    "status": "LIVE",
                    "owner": "owner-uuid",
                    "maker_address": "0x1234567890123456789012345678901234567890",
                    "market": "0xM1",
                    "asset_id": "%s",
                    "side": "BUY",
                    "original_size": "100000000",
                    "size_matched": "0",
                    "price": "0.5",
                    "outcome": "YES",
                    "expiration": "1735689600",
                    "order_type": "GTC",
                    "associate_trades": [],
                    "created_at": 1700000000
                  }
                ]
                """.formatted(TOKEN_ID));

        List<OpenOrder> orders = asyncClient.getOpenOrders().get();

        assertEquals(1, orders.size());
        assertEquals("0xORD1", orders.get(0).getId());
    }

    // -----------------------------------------------------------------------
    // TC-IT-307: Parallel requests complete independently via allOf
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-307: Two parallel getOrderBook() calls complete via CompletableFuture.allOf")
    void testParallelRequests() throws Exception {
        // Enqueue two responses (order is non-deterministic; both will succeed)
        enqueue("""
                {"market":"0xM1","asset_id":"TOKEN_A",
                 "bids":[{"price":"0.45","size":"100"}],
                 "asks":[{"price":"0.46","size":"150"}],
                 "tick_size":"0.01","neg_risk":false}
                """);
        enqueue("""
                {"market":"0xM2","asset_id":"TOKEN_B",
                 "bids":[{"price":"0.30","size":"200"}],
                 "asks":[{"price":"0.31","size":"300"}],
                 "tick_size":"0.01","neg_risk":false}
                """);

        CompletableFuture<OrderBookSummary> f1 = asyncClient.getOrderBook("TOKEN_A");
        CompletableFuture<OrderBookSummary> f2 = asyncClient.getOrderBook("TOKEN_B");

        CompletableFuture.allOf(f1, f2).join();

        assertNotNull(f1.get());
        assertNotNull(f2.get());
        assertEquals(2, server.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // TC-IT-308: Failed future wraps IOException as ExecutionException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-308: HTTP 500 error is wrapped in ExecutionException")
    void testHttpErrorWrappedInFuture() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"internal server error\"}")
                .addHeader("Content-Type", "application/json"));

        CompletableFuture<OrderBookSummary> future = asyncClient.getOrderBook(TOKEN_ID);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // -----------------------------------------------------------------------
    // TC-IT-309: getBalanceAllowance() async
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-309: getBalanceAllowance() completes with BalanceAllowanceResponse")
    void testGetBalanceAllowanceAsync() throws Exception {
        enqueue("{\"balance\":\"2000000000\",\"allowance\":\"1000000000\"}");

        BalanceAllowanceParams params = BalanceAllowanceParams.builder()
                .tokenId(TOKEN_ID)
                .assetType(AssetType.CONDITIONAL)
                .build();

        BalanceAllowanceResponse resp = asyncClient.getBalanceAllowance(params).get();

        assertNotNull(resp);
        assertEquals("2000000000", resp.getBalance());
        assertEquals("1000000000", resp.getAllowance());
    }

    // -----------------------------------------------------------------------
    // TC-IT-310: thenCombine of orderBook + midpoint
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-310: thenCombine chaining of getOrderBook + getMidpoint works")
    void testThenCombineBookAndMidpoint() throws Exception {
        enqueue("""
                {"market":"0xM","asset_id":"%s",
                 "bids":[{"price":"0.45","size":"100"}],
                 "asks":[{"price":"0.46","size":"150"}],
                 "tick_size":"0.01","neg_risk":false}
                """.formatted(TOKEN_ID));
        enqueue("{\"mid\":\"0.455\"}");

        record Summary(int bidCount, BigDecimal mid) {}

        // Use thenCompose to sequence midpoint after orderBook so responses are consumed in order
        CompletableFuture<Summary> combined = asyncClient.getOrderBook(TOKEN_ID)
                .thenCompose(book -> asyncClient.getMidpoint(TOKEN_ID)
                        .thenApply(mid -> new Summary(book.getBids().size(), mid)));

        Summary result = combined.get();

        assertEquals(1, result.bidCount());
        assertEquals(new BigDecimal("0.455"), result.mid());
    }

    // -----------------------------------------------------------------------
    // TC-IT-311: L2 auth guard fires in async wrapper too
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-311: L2 guard fires inside the future for unauthenticated async client")
    void testAsyncL2AuthGuard() {
        String mockBase = server.url("").toString().replaceAll("/$", "");
        PolymarketClient unauthSync = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .clobHost(mockBase)
                .build();
        AsyncPolymarketClient unauthAsync = AsyncPolymarketClient.wrap(unauthSync);

        CompletableFuture<List<OpenOrder>> future = unauthAsync.getOpenOrders();

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }
}
