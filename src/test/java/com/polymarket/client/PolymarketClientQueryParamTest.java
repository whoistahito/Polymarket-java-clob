package com.polymarket.client;

import com.polymarket.model.PriceHistoryFilterParams;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all public unauthenticated GET methods in {@link PolymarketClient}
 * send their filter parameters as URL query-string parameters, NOT as HTTP headers.
 *
 * <p>Before the fix, every such method called {@code http.get(url, params)} where the
 * second argument is treated as an HTTP headers map — silently ignoring all filters
 * and causing the API to return unfiltered (stale) results.
 */
@DisplayName("TC-QP — PolymarketClient query-parameter routing tests")
class PolymarketClientQueryParamTest {

    private static final String PK = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        client = new PolymarketClient.Builder()
                .privateKey(PK)
                .clobHost(baseUrl)
                .gammaHost(baseUrl)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -- helpers --

    private void enqueue(String body) {
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader("Content-Type", "application/json"));
    }

    private RecordedRequest take() throws InterruptedException {
        return server.takeRequest();
    }

    // ====================================================================== //
    // Gamma API — getGammaMarkets                                            //
    // ====================================================================== //

    @Test
    @DisplayName("TC-QP-001: getGammaMarkets sends params as query string, not headers")
    void getGammaMarketsUsesQueryString() throws Exception {
        enqueue("[]");

        Map<String, String> params = new HashMap<>();
        params.put("closed", "false");
        params.put("limit", "20");
        client.getGammaMarkets(params);

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("closed=false"), "Expected 'closed=false' in query string, got: " + path);
        assertTrue(path.contains("limit=20"), "Expected 'limit=20' in query string, got: " + path);
        assertNull(req.getHeader("closed"), "Param 'closed' must NOT be sent as a header");
        assertNull(req.getHeader("limit"), "Param 'limit' must NOT be sent as a header");
    }

    @Test
    @DisplayName("TC-QP-002: getGammaMarkets sends order and ascending as query params")
    void getGammaMarketsOrderAndAscending() throws Exception {
        enqueue("[]");

        Map<String, String> params = new HashMap<>();
        params.put("order", "volume24hr");
        params.put("ascending", "false");
        client.getGammaMarkets(params);

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("order=volume24hr"), "path: " + path);
        assertTrue(path.contains("ascending=false"), "path: " + path);
        assertNull(req.getHeader("order"));
        assertNull(req.getHeader("ascending"));
    }

    @Test
    @DisplayName("TC-QP-003: getGammaMarkets sends start_date_min as query param")
    void getGammaMarketsStartDateMin() throws Exception {
        enqueue("[]");

        Map<String, String> params = new HashMap<>();
        params.put("start_date_min", "2025-01-01T00:00:00Z");
        client.getGammaMarkets(params);

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("start_date_min="), "Expected 'start_date_min' in query string, got: " + path);
        assertNull(req.getHeader("start_date_min"));
    }

    @Test
    @DisplayName("TC-QP-004: getGammaMarkets with empty params produces no query string")
    void getGammaMarketsEmptyParams() throws Exception {
        enqueue("[]");
        client.getGammaMarkets(new HashMap<>());

        RecordedRequest req = take();
        assertFalse(req.getPath().contains("?"), "Empty params should produce no query string, got: " + req.getPath());
    }

    // ====================================================================== //
    // CLOB public market data — single-token queries                         //
    // ====================================================================== //

    @Test
    @DisplayName("TC-QP-010: getOrderBook sends token_id as query param, not header")
    void getOrderBookTokenIdInQueryString() throws Exception {
        enqueue("{\"market\":\"0xm\",\"asset_id\":\"tok1\",\"hash\":\"h\",\"bids\":[],\"asks\":[]}");

        // We care only about the outgoing request URL; parsing may fail on @Value model
        try { client.getOrderBook("tok1"); } catch (Exception ignored) {}

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tok1"), "path: " + path);
        assertNull(req.getHeader("token_id"), "token_id must NOT be sent as a header");
    }

    @Test
    @DisplayName("TC-QP-011: getMidpoint sends token_id as query param, not header")
    void getMidpointTokenIdInQueryString() throws Exception {
        enqueue("{\"mid\":\"0.50\"}");

        client.getMidpoint("tok2");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tok2"), "path: " + path);
        assertNull(req.getHeader("token_id"));
    }

    @Test
    @DisplayName("TC-QP-012: getPrice sends token_id and side as query params, not headers")
    void getPriceTokenIdAndSideInQueryString() throws Exception {
        enqueue("{\"price\":\"0.65\"}");

        client.getPrice("tok3", "BUY");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tok3"), "path: " + path);
        assertTrue(path.contains("side=BUY"), "path: " + path);
        assertNull(req.getHeader("token_id"));
        assertNull(req.getHeader("side"));
    }

    @Test
    @DisplayName("TC-QP-013: getSpread sends token_id as query param, not header")
    void getSpreadTokenIdInQueryString() throws Exception {
        enqueue("{\"spread\":\"0.01\"}");

        client.getSpread("tok4");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tok4"), "path: " + path);
        assertNull(req.getHeader("token_id"));
    }

    @Test
    @DisplayName("TC-QP-014: getLastTradePrice sends token_id as query param, not header")
    void getLastTradePriceTokenIdInQueryString() throws Exception {
        enqueue("{\"price\":\"0.60\"}");

        client.getLastTradePrice("tok5");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tok5"), "path: " + path);
        assertNull(req.getHeader("token_id"));
    }

    // ====================================================================== //
    // CLOB pagination — next_cursor routing                                  //
    // ====================================================================== //

    @Test
    @DisplayName("TC-QP-020: getMarkets sends next_cursor as query param, not header")
    void getMarketsCursorInQueryString() throws Exception {
        enqueue("{\"next_cursor\":\"end\",\"data\":[]}");

        client.getMarkets("MQ==");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("next_cursor=MQ=="), "path: " + path);
        assertNull(req.getHeader("next_cursor"), "next_cursor must NOT be sent as a header");
    }

    @Test
    @DisplayName("TC-QP-021: getMarkets(null) uses INITIAL_CURSOR in query string")
    void getMarketsNullCursorUsesDefault() throws Exception {
        enqueue("{\"next_cursor\":\"end\",\"data\":[]}");

        client.getMarkets(null);

        RecordedRequest req = take();
        String path = req.getPath();

        // INITIAL_CURSOR = "MA==" (base64 "0")
        assertTrue(path.contains("next_cursor="), "Expected next_cursor in query string, got: " + path);
        assertNull(req.getHeader("next_cursor"));
    }

    @Test
    @DisplayName("TC-QP-022: getSimplifiedMarkets sends next_cursor as query param, not header")
    void getSimplifiedMarketsCursorInQueryString() throws Exception {
        enqueue("{\"next_cursor\":\"end\",\"data\":[]}");

        // We care only about the outgoing request URL; parsing may fail on @Value model
        try { client.getSimplifiedMarkets(null); } catch (Exception ignored) {}

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("next_cursor="), "path: " + path);
        assertNull(req.getHeader("next_cursor"));
    }

    @Test
    @DisplayName("TC-QP-023: getSamplingMarkets sends next_cursor as query param, not header")
    void getSamplingMarketsCursorInQueryString() throws Exception {
        enqueue("{\"next_cursor\":\"end\",\"data\":[]}");

        // We care only about the outgoing request URL; parsing may fail on @Value model
        try { client.getSamplingMarkets(null); } catch (Exception ignored) {}

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("next_cursor="), "path: " + path);
        assertNull(req.getHeader("next_cursor"));
    }

    @Test
    @DisplayName("TC-QP-024: getSamplingSimplifiedMarkets sends next_cursor as query param, not header")
    void getSamplingSimplifiedMarketsCursorInQueryString() throws Exception {
        enqueue("{\"next_cursor\":\"end\",\"data\":[]}");

        // We care only about the outgoing request URL; parsing may fail on @Value model
        try { client.getSamplingSimplifiedMarkets(null); } catch (Exception ignored) {}

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("next_cursor="), "path: " + path);
        assertNull(req.getHeader("next_cursor"));
    }

    // ====================================================================== //
    // CLOB tick-size / fee-rate / neg-risk cache helpers                     //
    // ====================================================================== //

    @Test
    @DisplayName("TC-QP-030: getTickSize sends token_id as query param, not header")
    void getTickSizeTokenIdInQueryString() throws Exception {
        enqueue("{\"tick_size\":\"0.01\"}");

        client.getTickSize("tokTS");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tokTS"), "path: " + path);
        assertNull(req.getHeader("token_id"));
    }

    @Test
    @DisplayName("TC-QP-031: getFeeRateBps sends token_id as query param, not header")
    void getFeeRateBpsTokenIdInQueryString() throws Exception {
        enqueue("{\"fee_rate_bps\":0}");

        client.getFeeRateBps("tokFR");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tokFR"), "path: " + path);
        assertNull(req.getHeader("token_id"));
    }

    @Test
    @DisplayName("TC-QP-032: getNegRisk sends token_id as query param, not header")
    void getNegRiskTokenIdInQueryString() throws Exception {
        enqueue("{\"neg_risk\":false}");

        client.getNegRisk("tokNR");

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("token_id=tokNR"), "path: " + path);
        assertNull(req.getHeader("token_id"));
    }

    @Test
    @DisplayName("TC-QP-033: getTickSize uses cache on second call (only one HTTP request)")
    void getTickSizeUsesCache() throws Exception {
        enqueue("{\"tick_size\":\"0.01\"}");

        // First call hits the server
        String first = client.getTickSize("tokCache");
        // Second call must use cache (no second request queued)
        String second = client.getTickSize("tokCache");

        assertEquals("0.01", first);
        assertEquals("0.01", second);
        assertEquals(1, server.getRequestCount(), "Expected exactly 1 HTTP request due to caching");
    }

    // ====================================================================== //
    // Price history                                                           //
    // ====================================================================== //

    @Test
    @DisplayName("TC-QP-040: getPricesHistory sends market and fidelity as query params, not headers")
    void getPricesHistoryParamsInQueryString() throws Exception {
        enqueue("[]");

        PriceHistoryFilterParams params = PriceHistoryFilterParams.builder()
                .market("0xmarket1")
                .fidelity(100)
                .build();
        client.getPricesHistory(params);

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("market=0xmarket1"), "path: " + path);
        assertTrue(path.contains("fidelity=100"), "path: " + path);
        assertNull(req.getHeader("market"));
        assertNull(req.getHeader("fidelity"));
    }

    @Test
    @DisplayName("TC-QP-041: getPricesHistory sends startTs and endTs as query params")
    void getPricesHistoryStartEndTs() throws Exception {
        enqueue("[]");

        PriceHistoryFilterParams params = PriceHistoryFilterParams.builder()
                .market("0xm")
                .startTs(1700000000L)
                .endTs(1700086400L)
                .build();
        client.getPricesHistory(params);

        RecordedRequest req = take();
        String path = req.getPath();

        assertTrue(path.contains("startTs=1700000000"), "path: " + path);
        assertTrue(path.contains("endTs=1700086400"), "path: " + path);
        assertNull(req.getHeader("startTs"));
        assertNull(req.getHeader("endTs"));
    }
}
