package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OrdersScoringParams} model and the
 * {@code areOrdersScoring} / {@code isOrderScoring} methods on
 * {@link PolymarketClient} and {@link AsyncPolymarketClient}.
 *
 * <p>Test cases mirror the Rust SDK integration tests in
 * {@code rs-clob-client/tests/clob.rs}:
 * {@code is_order_scoring_should_succeed} and
 * {@code are_orders_scoring_should_succeed}.
 */
@DisplayName("TC-OSP — OrdersScoringParams tests")
class OrdersScoringParamsTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String FUNDER = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    // -------------------------------------------------------------------------
    // Model tests (no network)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-OSP-1xx — Model class")
    class ModelTests {

        @Test
        @DisplayName("TC-OSP-101 builder creates model with single order ID")
        void singleOrderId() {
            OrdersScoringParams params = OrdersScoringParams.builder()
                .orderId("abc-123")
                .build();

            assertEquals(List.of("abc-123"), params.getOrderIds());
        }

        @Test
        @DisplayName("TC-OSP-102 builder creates model with multiple order IDs")
        void multipleOrderIds() {
            OrdersScoringParams params = OrdersScoringParams.builder()
                .orderId("id-1")
                .orderId("id-2")
                .orderId("id-3")
                .build();

            assertEquals(List.of("id-1", "id-2", "id-3"), params.getOrderIds());
        }

        @Test
        @DisplayName("TC-OSP-103 builder accepts orderIds list directly")
        void orderIdsList() {
            List<String> ids = List.of("x", "y");
            OrdersScoringParams params = OrdersScoringParams.builder()
                .orderIds(ids)
                .build();

            assertEquals(ids, params.getOrderIds());
        }

        @Test
        @DisplayName("TC-OSP-104 null orderIds list throws NullPointerException")
        void nullListThrows() {
            assertThrows(NullPointerException.class, () ->
                OrdersScoringParams.builder().orderIds(null).build());
        }

        @Test
        @DisplayName("TC-OSP-105 empty orderIds list is allowed")
        void emptyListAllowed() {
            OrdersScoringParams params = OrdersScoringParams.builder()
                .orderIds(List.of())
                .build();

            assertTrue(params.getOrderIds().isEmpty());
        }

        @Test
        @DisplayName("TC-OSP-106 value equality: equal params are equal")
        void valueEquality() {
            OrdersScoringParams a = OrdersScoringParams.builder().orderId("1").orderId("2").build();
            OrdersScoringParams b = OrdersScoringParams.builder().orderId("1").orderId("2").build();

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("TC-OSP-107 toString contains order IDs")
        void toStringContainsIds() {
            OrdersScoringParams params = OrdersScoringParams.builder().orderId("my-order").build();
            assertTrue(params.toString().contains("my-order"));
        }
    }

    // -------------------------------------------------------------------------
    // PolymarketClient auth-guard tests (no network required)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-OSP-2xx — PolymarketClient auth guards")
    class AuthGuardTests {

        private PolymarketClient clientNoAuth;

        @BeforeEach
        void setUp() {
            clientNoAuth = new PolymarketClient.Builder()
                .privateKey(PK)
                .chainId(137)
                .build();
        }

        @Test
        @DisplayName("TC-OSP-201 isOrderScoring requires L2 auth")
        void isOrderScoringRequiresAuth() {
            assertThrows(IllegalStateException.class,
                () -> clientNoAuth.isOrderScoring("order-1"));
        }

        @Test
        @DisplayName("TC-OSP-202 areOrdersScoring(List) requires L2 auth")
        void areOrdersScoringListRequiresAuth() {
            assertThrows(IllegalStateException.class,
                () -> clientNoAuth.areOrdersScoring(List.of("order-1")));
        }

        @Test
        @DisplayName("TC-OSP-203 areOrdersScoring(OrdersScoringParams) requires L2 auth")
        void areOrdersScoringParamsRequiresAuth() {
            OrdersScoringParams params = OrdersScoringParams.builder().orderId("order-1").build();
            assertThrows(IllegalStateException.class,
                () -> clientNoAuth.areOrdersScoring(params));
        }
    }

    // -------------------------------------------------------------------------
    // HTTP integration tests (MockWebServer)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-OSP-3xx — HTTP round-trip (MockWebServer)")
    class HttpTests {

        private MockWebServer server;
        private PolymarketClient client;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            String base = server.url("/").toString();

            ApiKeyCreds creds = new ApiKeyCreds("test-key", "c2VjcmV0", "pass123");
            client = new PolymarketClient.Builder()
                .privateKey(PK)
                .funderAddress(FUNDER)
                .apiCreds(creds)
                .clobHost(base)
                .gammaHost(base)
                .build();
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        @Test
        @DisplayName("TC-OSP-301 isOrderScoring returns true for a scoring order")
        void isOrderScoringTrue() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"scoring\":true}")
                .addHeader("Content-Type", "application/json"));

            OrderScoring result = client.isOrderScoring("1");

            assertTrue(result.isScoring(), "Expected scoring=true");

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("GET", req.getMethod());
            assertTrue(req.getPath().startsWith("/order-scoring"),
                "Expected path /order-scoring, got: " + req.getPath());
            assertTrue(req.getPath().contains("order_id=1"),
                "Expected query param order_id=1, got: " + req.getPath());
        }

        @Test
        @DisplayName("TC-OSP-302 isOrderScoring returns false for a non-scoring order")
        void isOrderScoringFalse() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"scoring\":false}")
                .addHeader("Content-Type", "application/json"));

            OrderScoring result = client.isOrderScoring("nonscoring-id");

            assertFalse(result.isScoring());
        }

        @Test
        @DisplayName("TC-OSP-303 areOrdersScoring(List) posts JSON array body (Rust parity)")
        void areOrdersScoringListPostsJsonArray() throws Exception {
            // Rust SDK test: are_orders_scoring_should_succeed sends ["1"] and gets {"1": true}
            server.enqueue(new MockResponse()
                .setBody("{\"1\":true}")
                .addHeader("Content-Type", "application/json"));

            Map<String, Boolean> result = client.areOrdersScoring(List.of("1"));

            assertEquals(Map.of("1", true), result);

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("POST", req.getMethod());
            assertTrue(req.getPath().startsWith("/orders-scoring"),
                "Expected path /orders-scoring, got: " + req.getPath());
            // Body must be a JSON array, NOT wrapped in {"orderIds": [...]}
            String body = req.getBody().readUtf8();
            assertEquals("[\"1\"]", body,
                "Body should be a raw JSON array, got: " + body);
        }

        @Test
        @DisplayName("TC-OSP-304 areOrdersScoring(List) with multiple IDs")
        void areOrdersScoringMultipleIds() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"id-1\":true,\"id-2\":false,\"id-3\":true}")
                .addHeader("Content-Type", "application/json"));

            Map<String, Boolean> result = client.areOrdersScoring(List.of("id-1", "id-2", "id-3"));

            assertEquals(3, result.size());
            assertTrue(result.get("id-1"));
            assertFalse(result.get("id-2"));
            assertTrue(result.get("id-3"));

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            String body = req.getBody().readUtf8();
            // Verify array format
            assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Body must be a JSON array, got: " + body);
        }

        @Test
        @DisplayName("TC-OSP-305 areOrdersScoring(OrdersScoringParams) delegates to List overload")
        void areOrdersScoringParamsDelegates() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"1\":true}")
                .addHeader("Content-Type", "application/json"));

            OrdersScoringParams params = OrdersScoringParams.builder().orderId("1").build();
            Map<String, Boolean> result = client.areOrdersScoring(params);

            assertEquals(Map.of("1", true), result);

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("POST", req.getMethod());
            String body = req.getBody().readUtf8();
            assertEquals("[\"1\"]", body,
                "Body should be a raw JSON array, got: " + body);
        }

        @Test
        @DisplayName("TC-OSP-306 areOrdersScoring(OrdersScoringParams) with multiple IDs sends array")
        void areOrdersScoringParamsMultipleIds() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"a\":true,\"b\":true}")
                .addHeader("Content-Type", "application/json"));

            OrdersScoringParams params = OrdersScoringParams.builder()
                .orderId("a")
                .orderId("b")
                .build();
            Map<String, Boolean> result = client.areOrdersScoring(params);

            assertEquals(2, result.size());

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            String body = req.getBody().readUtf8();
            assertTrue(body.contains("\"a\"") && body.contains("\"b\""),
                "Body must contain both IDs: " + body);
            assertTrue(body.startsWith("["), "Body must be a JSON array: " + body);
        }
    }

    // -------------------------------------------------------------------------
    // AsyncPolymarketClient tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-OSP-4xx — AsyncPolymarketClient")
    class AsyncTests {

        private MockWebServer server;
        private AsyncPolymarketClient asyncClient;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            String base = server.url("/").toString();

            ApiKeyCreds creds = new ApiKeyCreds("test-key", "c2VjcmV0", "pass123");
            PolymarketClient sync = new PolymarketClient.Builder()
                .privateKey(PK)
                .funderAddress(FUNDER)
                .apiCreds(creds)
                .clobHost(base)
                .gammaHost(base)
                .build();
            asyncClient = AsyncPolymarketClient.wrap(sync);
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        @Test
        @DisplayName("TC-OSP-401 areOrdersScoring(List) returns CompletableFuture result")
        void asyncAreOrdersScoringList() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"1\":true}")
                .addHeader("Content-Type", "application/json"));

            Map<String, Boolean> result = asyncClient.areOrdersScoring(List.of("1")).get(5, TimeUnit.SECONDS);

            assertEquals(Map.of("1", true), result);
        }

        @Test
        @DisplayName("TC-OSP-402 areOrdersScoring(OrdersScoringParams) returns CompletableFuture result")
        void asyncAreOrdersScoringParams() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"1\":true}")
                .addHeader("Content-Type", "application/json"));

            OrdersScoringParams params = OrdersScoringParams.builder().orderId("1").build();
            Map<String, Boolean> result = asyncClient.areOrdersScoring(params).get(5, TimeUnit.SECONDS);

            assertEquals(Map.of("1", true), result);
        }

        @Test
        @DisplayName("TC-OSP-403 isOrderScoring returns CompletableFuture result")
        void asyncIsOrderScoring() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"scoring\":true}")
                .addHeader("Content-Type", "application/json"));

            OrderScoring result = asyncClient.isOrderScoring("1").get(5, TimeUnit.SECONDS);

            assertTrue(result.isScoring());
        }
    }
}
