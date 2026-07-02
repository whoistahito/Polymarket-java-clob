package com.polymarket.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.client.PolymarketClient;
import com.polymarket.model.OrderBookSummary;
import com.polymarket.model.SpreadResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the PolymarketClient public (no-auth) API surface.
 *
 * <p>Uses {@link MockWebServer} to serve pre-recorded API responses so no real
 * network call is ever made.  All tests follow the TC-IT-XXX naming scheme.
 */
@DisplayName("PolymarketClient Public API Integration Tests")
class PolymarketClientPublicApiIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String TOKEN_ID =
            "71321045679252212594626385532706912750332728571942532289631379312455583992563";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        String mockBase = server.url("").toString().replaceAll("/$", "");
        client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .chainId(137)
                .clobHost(mockBase)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void enqueue(int statusCode, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setBody(body)
                .addHeader("Content-Type", "application/json"));
    }

    private void enqueue(String body) {
        enqueue(200, body);
    }

    // -----------------------------------------------------------------------
    // TC-IT-001: getServerTime
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-001: getServerTime() parses the epoch-second response")
    void testGetServerTime() throws Exception {
        enqueue("1700000000");

        long time = client.getServerTime();

        assertEquals(1700000000L, time);
        RecordedRequest req = server.takeRequest();
        assertEquals("/time", req.getPath());
        assertEquals("GET", req.getMethod());
    }

    // -----------------------------------------------------------------------
    // TC-IT-002: getMarkets
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-002: getMarkets() deserialises paginated market list")
    void testGetMarkets() throws Exception {
        enqueue("""
                {
                  "limit": 100,
                  "next_cursor": "MTAw",
                  "count": 2,
                  "data": [
                    {
                      "condition_id": "0x1111",
                      "question_id": "0xaaaa",
                      "tokens": [],
                      "rewards": {"min_size": 1, "max_spread": 0.01}
                    },
                    {
                      "condition_id": "0x2222",
                      "question_id": "0xbbbb",
                      "tokens": [],
                      "rewards": {"min_size": 1, "max_spread": 0.01}
                    }
                  ]
                }
                """);

        Map<String, Object> result = client.getMarkets(null);

        assertNotNull(result);
        assertEquals("MTAw", result.get("next_cursor"));
        assertTrue(result.get("data") instanceof java.util.List<?>);
        assertEquals(2, ((java.util.List<?>) result.get("data")).size());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().startsWith("/markets"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-003: getMarket (single condition ID)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-003: getMarket(conditionId) deserialises a single market")
    void testGetMarket() throws Exception {
        String conditionId = "0xabc123def456789012345678901234567890123456789012345678901234567890";
        enqueue("""
                {
                  "condition_id": "%s",
                  "question_id": "0xqqqq",
                  "tokens": [],
                  "rewards": {"min_size": 1, "max_spread": 0.01}
                }
                """.formatted(conditionId));

        Map<String, Object> result = client.getMarket(conditionId);

        assertNotNull(result);
        assertEquals(conditionId, result.get("condition_id"));

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains(conditionId));
    }

    // -----------------------------------------------------------------------
    // TC-IT-004: getOrderBook
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-004: getOrderBook() deserialises bids and asks")
    void testGetOrderBook() throws Exception {
        enqueue("""
                {
                  "market": "0xmarket123",
                  "asset_id": "%s",
                  "timestamp": "1700000001",
                  "hash": "abcdef1234",
                  "bids": [
                    {"price": "0.45", "size": "100"},
                    {"price": "0.44", "size": "200"}
                  ],
                  "asks": [
                    {"price": "0.46", "size": "150"},
                    {"price": "0.47", "size": "250"}
                  ],
                  "min_order_size": "1",
                  "tick_size": "0.01",
                  "neg_risk": false
                }
                """.formatted(TOKEN_ID));

        OrderBookSummary book = client.getOrderBook(TOKEN_ID);

        assertNotNull(book);
        assertEquals("0xmarket123", book.getMarket());
        assertEquals(TOKEN_ID, book.getAssetId());
        assertEquals("0.01", book.getTickSize());
        assertFalse(book.getNegRisk());
        assertNotNull(book.getBids());
        assertEquals(2, book.getBids().size());
        assertEquals("0.45", book.getBids().get(0).getPrice());
        assertNotNull(book.getAsks());
        assertEquals(2, book.getAsks().size());
        assertEquals("0.46", book.getAsks().get(0).getPrice());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().startsWith("/book"));
        assertTrue(req.getPath().contains(TOKEN_ID));
    }

    // -----------------------------------------------------------------------
    // TC-IT-005: getMidpoint
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-005: getMidpoint() parses the mid field")
    void testGetMidpoint() throws Exception {
        enqueue("{\"mid\": \"0.455\"}");

        BigDecimal mid = client.getMidpoint(TOKEN_ID);

        assertNotNull(mid);
        assertEquals(new BigDecimal("0.455"), mid);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/midpoint"));
        assertTrue(req.getPath().contains(TOKEN_ID));
    }

    // -----------------------------------------------------------------------
    // TC-IT-006: getPrice (BUY side)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-006: getPrice(BUY) parses the price field")
    void testGetPriceBuy() throws Exception {
        enqueue("{\"price\": \"0.46\"}");

        BigDecimal price = client.getPrice(TOKEN_ID, "BUY");

        assertEquals(new BigDecimal("0.46"), price);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/price"));
        assertTrue(req.getPath().contains("BUY"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-007: getSpread
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-007: getSpread() populates SpreadResult with tokenId and spread")
    void testGetSpread() throws Exception {
        enqueue("{\"spread\": \"0.01\"}");

        SpreadResult result = client.getSpread(TOKEN_ID);

        assertNotNull(result);
        assertEquals(TOKEN_ID, result.getTokenId());
        assertEquals(new BigDecimal("0.01"), result.getSpread());
    }

    // -----------------------------------------------------------------------
    // TC-IT-008: getLastTradePrice
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-008: getLastTradePrice() parses the price field")
    void testGetLastTradePrice() throws Exception {
        enqueue("{\"price\": \"0.45\", \"side\": \"BUY\"}");

        var ltp = client.getLastTradePrice(TOKEN_ID);

        assertEquals(new BigDecimal("0.45"), ltp.getPrice());
    }

    // -----------------------------------------------------------------------
    // TC-IT-009: getTickSize (fetches and caches)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-009: getTickSize() fetches from server and caches the result")
    void testGetTickSizeCaches() throws Exception {
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");

        // First call — hits the server
        String ts1 = client.getTickSize(TOKEN_ID);
        assertEquals("0.01", ts1);
        assertEquals(1, server.getRequestCount());

        // Second call — served from cache, no new request
        String ts2 = client.getTickSize(TOKEN_ID);
        assertEquals("0.01", ts2);
        assertEquals(1, server.getRequestCount()); // still 1
    }

    // -----------------------------------------------------------------------
    // TC-IT-010: getTickSize cache eviction via clearTickSizeCache(tokenId)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-010: clearTickSizeCache(tokenId) forces a fresh server fetch")
    void testClearTickSizeCacheByToken() throws Exception {
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");
        enqueue("{\"tick_size\": \"0.1\",  \"minimum\": 0.1}");

        String first = client.getTickSize(TOKEN_ID);
        assertEquals("0.01", first);

        client.clearTickSizeCache(TOKEN_ID);

        String second = client.getTickSize(TOKEN_ID);
        assertEquals("0.1", second);
        assertEquals(2, server.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // TC-IT-011: getFeeRateBps (fetches and caches)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-011: getFeeRateBps() fetches from server and caches the result")
    void testGetFeeRateBpsCaches() throws Exception {
        enqueue("{\"fee_rate_bps\": 30}");

        int fee = client.getFeeRateBps(TOKEN_ID);
        assertEquals(30, fee);
        assertEquals(1, server.getRequestCount());

        // Second call — cache hit
        int fee2 = client.getFeeRateBps(TOKEN_ID);
        assertEquals(30, fee2);
        assertEquals(1, server.getRequestCount());
    }

  @Test
  @DisplayName("TC-IT-011b: metadata endpoints accept alias keys used by newer API responses")
  void testMetadataAliasKeysAreAccepted() throws Exception {
    enqueue("{\"minimum_tick_size\": \"0.001\"}");
    enqueue("{\"base_fee\": \"45\"}");

    String tick = client.getTickSize(TOKEN_ID);
    int fee = client.getFeeRateBps(TOKEN_ID);

    assertEquals("0.001", tick);
    assertEquals(45, fee);
  }

  @Test
  @DisplayName("TC-IT-011c: metadata endpoints throw IOException when required fields are missing")
  void testMetadataMissingFieldsFailFast() {
    enqueue("{\"unexpected\": \"value\"}");

    assertThrows(IOException.class, () -> client.getTickSize(TOKEN_ID));
  }

    // -----------------------------------------------------------------------
    // TC-IT-012: getOk (health check)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-012: getOk() returns server status map")
    void testGetOk() throws Exception {
        enqueue("{\"status\": \"ok\"}");

        Map<String, Object> result = client.getOk();

        assertNotNull(result);
        assertEquals("ok", result.get("status"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-013: getOrderBook hash is computed from book contents
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-013: getOrderBookHash() returns a non-empty SHA-1 hex digest")
    void testGetOrderBookHash() throws Exception {
        enqueue("""
                {
                  "market": "0xm1",
                  "asset_id": "%s",
                  "timestamp": "1700000002",
                  "bids": [{"price": "0.45", "size": "100"}],
                  "asks": [{"price": "0.46", "size": "150"}],
                  "tick_size": "0.01",
                  "neg_risk": false
                }
                """.formatted(TOKEN_ID));

        OrderBookSummary book = client.getOrderBook(TOKEN_ID);
        String hash = client.getOrderBookHash(book);

        assertNotNull(hash);
        assertFalse(hash.isBlank());
        // SHA-1 hex is 40 chars
        assertEquals(40, hash.length());
    }

    // -----------------------------------------------------------------------
    // TC-IT-014: HTTP 4xx error propagates as IOException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-014: 404 response from server throws IOException")
    void testHttpErrorPropagates() {
        enqueue(404, "{\"error\": \"not found\"}");

        assertThrows(IOException.class, () -> client.getOrderBook(TOKEN_ID));
    }

    // -----------------------------------------------------------------------
    // TC-IT-015: getNegRisk
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-015: getNegRisk() parses boolean neg_risk flag")
    void testGetNegRisk() throws Exception {
        enqueue("{\"neg_risk\": true}");

        boolean negRisk = client.getNegRisk(TOKEN_ID);

        assertTrue(negRisk);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/neg-risk"));
    }
}
