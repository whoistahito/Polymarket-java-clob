package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.polymarket.model.BookParams;
import com.polymarket.model.Side;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PMK-008: bulk market-data endpoints return JSON objects keyed by token id, not arrays.
 * These pin the map shapes so the old List parse (which threw at runtime) cannot return.
 */
@DisplayName("TC-BMD — bulk market-data object responses")
class BulkMarketDataTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        client = new PolymarketClient.Builder().privateKey(PK).clobHost(baseUrl).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(
            new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    private static List<BookParams> req(String... ids) {
        return List.of(BookParams.builder().tokenId(ids[0]).build());
    }

    @Test
    @DisplayName("TC-BMD-001 getMidpoints parses {tokenId: mid} object into a map")
    void midpoints() throws Exception {
        enqueue("{\"123\":\"0.51\",\"456\":\"0.62\"}");
        Map<String, BigDecimal> m = client.getMidpoints(req("123"));
        assertEquals(0, new BigDecimal("0.51").compareTo(m.get("123")));
        assertEquals(0, new BigDecimal("0.62").compareTo(m.get("456")));
    }

    @Test
    @DisplayName("TC-BMD-002 getPrices parses {tokenId: {side: price}} nested object")
    void prices() throws Exception {
        enqueue("{\"123\":{\"BUY\":\"0.51\",\"SELL\":\"0.52\"}}");
        Map<String, Map<Side, BigDecimal>> m = client.getPrices(req("123"));
        assertEquals(0, new BigDecimal("0.51").compareTo(m.get("123").get(Side.BUY)));
        assertEquals(0, new BigDecimal("0.52").compareTo(m.get("123").get(Side.SELL)));
    }

    @Test
    @DisplayName("TC-BMD-003 getSpreads parses {tokenId: spread} object into a map")
    void spreads() throws Exception {
        enqueue("{\"123\":\"0.01\"}");
        Map<String, BigDecimal> m = client.getSpreads(req("123"));
        assertEquals(0, new BigDecimal("0.01").compareTo(m.get("123")));
    }
}
