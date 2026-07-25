package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.MarketRules;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 024 — {@code getMarketRules} reads the tick and minimum without a {@code double} round trip.
 *
 * <p>The pre-existing {@code getMarket} returns an untyped map whose JSON numbers Jackson has already
 * coerced to {@code Double}, so an exact decimal is destroyed before any caller sees it. These tests
 * pin the typed path against the same response.
 */
@DisplayName("TC-GMR — getMarketRules exactness (Ticket 024)")
class GetMarketRulesTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PolymarketClient.Builder()
            .privateKey(PK)
            .clobHost(server.url("/").toString())
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("TC-GMR-001 an exact minimum survives the HTTP read")
    void exactMinimumSurvivesHttpRead() throws Exception {
        enqueue("{\"condition_id\":\"0xabc\","
            + "\"minimum_tick_size\":\"0.0025\",\"minimum_order_size\":5.0000000000000001}");

        MarketRules rules = client.getMarketRules("0xabc");

        assertEquals(new BigDecimal("5.0000000000000001"), rules.orderMinSize());
        assertEquals(new BigDecimal("0.0025"), rules.orderPriceMinTickSize());
        assertTrue(rules.isValid());
    }

    @Test
    @DisplayName("TC-GMR-002 the rules convert straight into CreateOrderOptions")
    void convertsToCreateOrderOptions() throws Exception {
        enqueue("{\"minimum_tick_size\":0.005,\"minimum_order_size\":10.005}");

        MarketRules rules = client.getMarketRules("0xabc");

        assertEquals("0.005", rules.toCreateOrderOptions(false).tickSize());
        assertEquals(new BigDecimal("10.005"), rules.toCreateOrderOptions(false).orderMinSize());
        assertEquals(Boolean.FALSE, rules.toCreateOrderOptions(false).negRisk());
    }

    @Test
    @DisplayName("TC-GMR-003 a response missing the rules yields nulls, not defaults")
    void missingRulesYieldNulls() throws Exception {
        enqueue("{\"condition_id\":\"0xabc\",\"active\":true}");

        MarketRules rules = client.getMarketRules("0xabc");

        assertNull(rules.orderPriceMinTickSize());
        assertNull(rules.orderMinSize());
        assertFalse(rules.isComplete());
        assertFalse(rules.isValid());
        assertNull(rules.tickSizeString());
    }

    @Test
    @DisplayName("TC-GMR-004 the async wrapper returns the same typed rules")
    void asyncReturnsTypedRules() throws Exception {
        enqueue("{\"minimum_tick_size\":0.01,\"minimum_order_size\":5}");

        MarketRules rules =
            AsyncPolymarketClient.wrap(client).getMarketRules("0xabc").get(10, TimeUnit.SECONDS);

        assertEquals(0, new BigDecimal("0.01").compareTo(rules.orderPriceMinTickSize()));
        assertTrue(rules.isValid());
    }
}
