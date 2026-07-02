package com.polymarket.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for PolymarketEndpoints.
 * Verifies all endpoint constants and URL helper methods match TypeScript SDK.
 */
@DisplayName("PolymarketEndpoints Tests")
class PolymarketEndpointsTest {

    @Test
    @DisplayName("TC-EP-001: Base URLs are correct")
    void testBaseUrls() {
        assertEquals("https://clob.polymarket.com", PolymarketEndpoints.CLOB_BASE_URL);
        assertEquals("https://gamma-api.polymarket.com", PolymarketEndpoints.GAMMA_BASE_URL);
    }

    @Test
    @DisplayName("TC-EP-002: Auth endpoints match TypeScript SDK")
    void testAuthEndpointsMatchTypeScript() {
        assertEquals("/auth/api-key", PolymarketEndpoints.CLOB_CREATE_API_KEY);
        assertEquals("/auth/api-keys", PolymarketEndpoints.CLOB_GET_API_KEYS);
        assertEquals("/auth/api-key", PolymarketEndpoints.CLOB_DELETE_API_KEY);
        assertEquals("/auth/derive-api-key", PolymarketEndpoints.CLOB_DERIVE_API_KEY);
    }

    @Test
    @DisplayName("TC-EP-003: Market endpoints match TypeScript SDK")
    void testMarketEndpointsMatchTypeScript() {
        assertEquals("/markets", PolymarketEndpoints.CLOB_GET_MARKETS);
        assertEquals("/markets/", PolymarketEndpoints.CLOB_GET_MARKET_PREFIX);
        assertEquals("/book", PolymarketEndpoints.CLOB_ORDER_BOOK);
        assertEquals("/books", PolymarketEndpoints.CLOB_ORDER_BOOKS);
        assertEquals("/price", PolymarketEndpoints.CLOB_PRICE);
        assertEquals("/prices", PolymarketEndpoints.CLOB_PRICES);
        assertEquals("/spread", PolymarketEndpoints.CLOB_SPREAD);
        assertEquals("/spreads", PolymarketEndpoints.CLOB_SPREADS);
        assertEquals("/tick-size", PolymarketEndpoints.CLOB_TICK_SIZE);
        assertEquals("/neg-risk", PolymarketEndpoints.CLOB_NEG_RISK);
        assertEquals("/fee-rate", PolymarketEndpoints.CLOB_FEE_RATE);
        assertEquals("/midpoint", PolymarketEndpoints.CLOB_MIDPOINT);
        assertEquals("/midpoints", PolymarketEndpoints.CLOB_MIDPOINTS);
        assertEquals("/last-trade-price", PolymarketEndpoints.CLOB_LAST_TRADE_PRICE);
        assertEquals("/last-trades-prices", PolymarketEndpoints.CLOB_LAST_TRADES_PRICES);
    }

    @Test
    @DisplayName("TC-EP-004: Order endpoints match TypeScript SDK")
    void testOrderEndpointsMatchTypeScript() {
        assertEquals("/order", PolymarketEndpoints.CLOB_POST_ORDER);
        assertEquals("/orders", PolymarketEndpoints.CLOB_POST_ORDERS);
        assertEquals("/order", PolymarketEndpoints.CLOB_CANCEL_ORDER);
        assertEquals("/orders", PolymarketEndpoints.CLOB_CANCEL_ORDERS);
        assertEquals("/cancel-all", PolymarketEndpoints.CLOB_CANCEL_ALL);
        assertEquals("/cancel-market-orders", PolymarketEndpoints.CLOB_CANCEL_MARKET_ORDERS);
        assertEquals("/data/order/", PolymarketEndpoints.CLOB_GET_ORDER_PREFIX);
        assertEquals("/data/orders", PolymarketEndpoints.CLOB_GET_ORDERS);
        assertEquals("/data/trades", PolymarketEndpoints.CLOB_GET_TRADES);
        assertEquals("/order-scoring", PolymarketEndpoints.CLOB_ORDER_SCORING);
        assertEquals("/orders-scoring", PolymarketEndpoints.CLOB_ORDERS_SCORING);
    }

    @Test
    @DisplayName("TC-EP-005: Header constants match TypeScript SDK")
    void testHeaderConstants() {
        assertEquals("POLY_ADDRESS", PolymarketEndpoints.HDR_POLY_ADDRESS);
        assertEquals("POLY_SIGNATURE", PolymarketEndpoints.HDR_POLY_SIGNATURE);
        assertEquals("POLY_TIMESTAMP", PolymarketEndpoints.HDR_POLY_TIMESTAMP);
        assertEquals("POLY_NONCE", PolymarketEndpoints.HDR_POLY_NONCE);
        assertEquals("POLY_API_KEY", PolymarketEndpoints.HDR_POLY_API_KEY);
        assertEquals("POLY_PASSPHRASE", PolymarketEndpoints.HDR_POLY_PASSPHRASE);
    }

    @Test
    @DisplayName("TC-EP-006: URL helper - normal case")
    void testUrlHelperNormalCase() {
        String url = PolymarketEndpoints.url("https://example.com", "/api/test");
        assertEquals("https://example.com/api/test", url);
    }

    @Test
    @DisplayName("TC-EP-007: URL helper - trailing slash on base")
    void testUrlHelperTrailingSlash() {
        String url = PolymarketEndpoints.url("https://example.com/", "/api/test");
        assertEquals("https://example.com/api/test", url);
    }

    @Test
    @DisplayName("TC-EP-008: URL helper - no leading slash on path")
    void testUrlHelperNoLeadingSlash() {
        String url = PolymarketEndpoints.url("https://example.com", "api/test");
        assertEquals("https://example.com/api/test", url);
    }

    @Test
    @DisplayName("TC-EP-009: CLOB URL convenience method")
    void testClobUrlMethod() {
        String url = PolymarketEndpoints.clobUrl("/markets");
        assertEquals("https://clob.polymarket.com/markets", url);
    }

    @Test
    @DisplayName("TC-EP-010: Gamma URL convenience method")
    void testGammaUrlMethod() {
        String url = PolymarketEndpoints.gammaUrl("/markets");
        assertEquals("https://gamma-api.polymarket.com/markets", url);
    }

    @Test
    @DisplayName("TC-EP-011: URL helper rejects null base URL")
    void testUrlHelperRejectsNullBaseUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> PolymarketEndpoints.url(null, "/test"));
    }

    @Test
    @DisplayName("TC-EP-012: URL helper rejects blank base URL")
    void testUrlHelperRejectsBlankBaseUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> PolymarketEndpoints.url("", "/test"));

        assertThrows(IllegalArgumentException.class,
                () -> PolymarketEndpoints.url("   ", "/test"));
    }

    @Test
    @DisplayName("TC-EP-013: URL helper rejects null path")
    void testUrlHelperRejectsNullPath() {
        assertThrows(IllegalArgumentException.class,
                () -> PolymarketEndpoints.url("https://example.com", null));
    }

    @Test
    @DisplayName("TC-EP-014: URL helper rejects blank path")
    void testUrlHelperRejectsBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> PolymarketEndpoints.url("https://example.com", ""));

        assertThrows(IllegalArgumentException.class,
                () -> PolymarketEndpoints.url("https://example.com", "   "));
    }

    @Test
    @DisplayName("TC-EP-015: Readonly API key endpoints")
    void testReadonlyApiKeyEndpoints() {
        assertEquals("/auth/readonly-api-key", PolymarketEndpoints.CLOB_CREATE_READONLY_API_KEY);
        assertEquals("/auth/readonly-api-keys", PolymarketEndpoints.CLOB_GET_READONLY_API_KEYS);
        assertEquals("/auth/readonly-api-key", PolymarketEndpoints.CLOB_DELETE_READONLY_API_KEY);
        assertEquals("/auth/validate-readonly-api-key", PolymarketEndpoints.CLOB_VALIDATE_READONLY_API_KEY);
    }

    @Test
    @DisplayName("TC-EP-016: Balance and allowance endpoints")
    void testBalanceAllowanceEndpoints() {
        assertEquals("/balance-allowance", PolymarketEndpoints.CLOB_BALANCE_ALLOWANCE);
        assertEquals("/balance-allowance/update", PolymarketEndpoints.CLOB_UPDATE_BALANCE_ALLOWANCE);
    }

    @Test
    @DisplayName("TC-EP-017: Heartbeat endpoint")
    void testHeartbeatEndpoint() {
        assertEquals("/v1/heartbeats", PolymarketEndpoints.CLOB_HEARTBEAT);
    }

    @Test
    @DisplayName("TC-EP-018: Notifications endpoint")
    void testNotificationsEndpoint() {
        assertEquals("/notifications", PolymarketEndpoints.CLOB_NOTIFICATIONS);
    }

    @Test
    @DisplayName("TC-EP-019: Rewards endpoints")
    void testRewardsEndpoints() {
        assertEquals("/rewards/user", PolymarketEndpoints.CLOB_REWARDS_USER);
        assertEquals("/rewards/user/total", PolymarketEndpoints.CLOB_REWARDS_USER_TOTAL);
        assertEquals("/rewards/user/percentages", PolymarketEndpoints.CLOB_REWARDS_PERCENTAGES);
        assertEquals("/rewards/markets/current", PolymarketEndpoints.CLOB_REWARDS_MARKETS_CURRENT);
        assertEquals("/rewards/markets/", PolymarketEndpoints.CLOB_REWARDS_MARKETS_PREFIX);
        assertEquals("/rewards/user/markets", PolymarketEndpoints.CLOB_REWARDS_USER_MARKETS);
    }

    @Test
    @DisplayName("TC-EP-020: Prices history endpoint")
    void testPricesHistoryEndpoint() {
        assertEquals("/prices-history", PolymarketEndpoints.CLOB_PRICES_HISTORY);
    }

    @Test
    @DisplayName("TC-EP-021: Live activity endpoints")
    void testLiveActivityEndpoints() {
        assertEquals("/live-activity/events/", PolymarketEndpoints.CLOB_LIVE_ACTIVITY_EVENTS_PREFIX);
    }

    @Test
    @DisplayName("TC-EP-022: Ban status endpoint")
    void testBanStatusEndpoint() {
        assertEquals("/auth/ban-status/closed-only", PolymarketEndpoints.CLOB_CLOSED_ONLY);
    }

    @Test
    @DisplayName("TC-EP-023: Gamma markets endpoint")
    void testGammaMarketsEndpoint() {
        assertEquals("/markets", PolymarketEndpoints.GAMMA_MARKETS);
    }

    @Test
    @DisplayName("TC-EP-024: URL helper handles both trailing slash and leading slash")
    void testUrlHelperBothSlashes() {
        String url = PolymarketEndpoints.url("https://example.com/", "/api/test");
        assertEquals("https://example.com/api/test", url);
    }

    @Test
    @DisplayName("TC-EP-025: URL helper handles neither trailing nor leading slash")
    void testUrlHelperNoSlashes() {
        String url = PolymarketEndpoints.url("https://example.com", "api/test");
        assertEquals("https://example.com/api/test", url);
    }

    @Test
    @DisplayName("TC-EP-026: CLOB URL with various paths")
    void testClobUrlWithVariousPaths() {
        assertEquals("https://clob.polymarket.com/order", PolymarketEndpoints.clobUrl("/order"));
        assertEquals("https://clob.polymarket.com/auth/api-key", PolymarketEndpoints.clobUrl("/auth/api-key"));
        assertEquals("https://clob.polymarket.com/data/orders", PolymarketEndpoints.clobUrl("/data/orders"));
    }

    @Test
    @DisplayName("TC-EP-027: Gamma URL with various paths")
    void testGammaUrlWithVariousPaths() {
        assertEquals("https://gamma-api.polymarket.com/markets", PolymarketEndpoints.gammaUrl("/markets"));
        assertEquals("https://gamma-api.polymarket.com/events", PolymarketEndpoints.gammaUrl("/events"));
    }
}
