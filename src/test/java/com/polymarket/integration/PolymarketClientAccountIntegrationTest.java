package com.polymarket.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.BuilderApiKey;
import com.polymarket.model.BuilderApiKeyResponse;
import com.polymarket.model.DropNotificationParams;
import com.polymarket.model.MarketReward;
import com.polymarket.model.Notification;
import com.polymarket.model.ReadonlyApiKeyResponse;
import com.polymarket.model.TotalUserEarning;
import com.polymarket.model.UserEarning;
import com.polymarket.model.UserRewardsEarning;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for PolymarketClient's API-key lifecycle, notifications, and
 * rewards endpoints (docs.polymarket.com/api-reference/authentication, /rewards,
 * /builders). Uses {@link MockWebServer} so no real network call is ever made.
 */
@DisplayName("PolymarketClient Account & Rewards Integration Tests")
class PolymarketClientAccountIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final ApiKeyCreds TEST_CREDS =
            new ApiKeyCreds("test-api-key-uuid", "c2VjcmV0MTIzNDU2Nzg=", "test-passphrase");
    private static final String END_CURSOR = "LTE="; // sentinel that stops auto-pagination

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
                .apiCreds(TEST_CREDS)
                .build();
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
    // API key lifecycle (L1 EIP-712 auth)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-120: createApiKey() POSTs to /auth/api-key and parses creds")
    void testCreateApiKey() throws Exception {
        enqueue("{\"apiKey\":\"k\",\"secret\":\"s\",\"passphrase\":\"p\"}");

        ApiKeyCreds creds = client.createApiKey();

        assertEquals("k", creds.getKey());
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/auth/api-key", req.getPath());
    }

    @Test
    @DisplayName("TC-IT-121: deriveApiKey() GETs /auth/derive-api-key and parses creds")
    void testDeriveApiKey() throws Exception {
        enqueue("{\"apiKey\":\"k2\",\"secret\":\"s2\",\"passphrase\":\"p2\"}");

        ApiKeyCreds creds = client.deriveApiKey();

        assertEquals("k2", creds.getKey());
        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/auth/derive-api-key", req.getPath());
    }

    @Test
    @DisplayName("TC-IT-122: deleteApiKey() sends DELETE /auth/api-key")
    void testDeleteApiKey() throws Exception {
        enqueue("{}");

        client.deleteApiKey();

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/auth/api-key", req.getPath());
    }

    // -----------------------------------------------------------------------
    // Readonly API keys
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-123: createReadonlyApiKey() POSTs and returns the new key")
    void testCreateReadonlyApiKey() throws Exception {
        enqueue("{\"apiKey\":\"ro-key-1\"}");

        ReadonlyApiKeyResponse response = client.createReadonlyApiKey();

        assertEquals("ro-key-1", response.getApiKey());
        assertEquals("/auth/readonly-api-key", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-IT-124: getReadonlyApiKeys() deserialises a list of key strings")
    void testGetReadonlyApiKeys() throws Exception {
        enqueue("[\"ro-key-1\", \"ro-key-2\"]");

        List<String> keys = client.getReadonlyApiKeys();

        assertEquals(List.of("ro-key-1", "ro-key-2"), keys);
        assertEquals("/auth/readonly-api-keys", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-IT-125: deleteReadonlyApiKey(key) sends the key in the DELETE body")
    void testDeleteReadonlyApiKey() throws Exception {
        enqueue("{}");

        client.deleteReadonlyApiKey("ro-key-1");

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/auth/readonly-api-key", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("ro-key-1"));
    }

    @Test
    @DisplayName("TC-IT-126: validateReadonlyApiKey(address, key) is an unauthenticated GET")
    void testValidateReadonlyApiKey() throws Exception {
        enqueue("{\"valid\": true}");

        String response = client.validateReadonlyApiKey("0xabc", "ro-key-1");

        assertTrue(response.contains("valid"));
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/auth/validate-readonly-api-key"));
        assertTrue(req.getPath().contains("address=0xabc"));
        assertTrue(req.getPath().contains("key=ro-key-1"));
    }

    // -----------------------------------------------------------------------
    // Builder API keys
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-127: createBuilderApiKey() POSTs and returns key/secret/passphrase")
    void testCreateBuilderApiKey() throws Exception {
        enqueue("{\"key\":\"b-key\",\"secret\":\"b-secret\",\"passphrase\":\"b-pass\"}");

        BuilderApiKey key = client.createBuilderApiKey();

        assertEquals("b-key", key.getKey());
        assertEquals("b-secret", key.getSecret());
        assertEquals("/auth/builder-api-key", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-IT-128: getBuilderApiKeys() deserialises the listing")
    void testGetBuilderApiKeys() throws Exception {
        enqueue("[{\"key\":\"b-key\",\"createdAt\":\"2024-01-01\",\"revokedAt\":null}]");

        List<BuilderApiKeyResponse> keys = client.getBuilderApiKeys();

        assertEquals(1, keys.size());
        assertEquals("b-key", keys.get(0).getKey());
        assertNull(keys.get(0).getRevokedAt());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/auth/builder-api-key", req.getPath());
    }

    @Test
    @DisplayName("TC-IT-129: revokeBuilderApiKey() sends DELETE /auth/builder-api-key")
    void testRevokeBuilderApiKey() throws Exception {
        enqueue("{}");

        client.revokeBuilderApiKey();

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/auth/builder-api-key", req.getPath());
    }

    // -----------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-130: getNotifications() deserialises typed notifications")
    void testGetNotifications() throws Exception {
        enqueue("[{\"type\": 1, \"owner\": \"0xabc\", \"payload\": {\"msg\": \"hi\"}}]");

        List<Notification> notifications = client.getNotifications();

        assertEquals(1, notifications.size());
        assertEquals(1, notifications.get(0).getType());
        assertEquals("0xabc", notifications.get(0).getOwner());
        assertEquals("/notifications", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-IT-131: dropNotifications(ids) sends the id list in the DELETE body")
    void testDropNotifications() throws Exception {
        enqueue("{}");

        client.dropNotifications(DropNotificationParams.builder().ids(List.of("n1", "n2")).build());

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/notifications", req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("n1") && body.contains("n2"));
    }

    // -----------------------------------------------------------------------
    // Rewards
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-140: getCurrentRewards() auto-paginates market reward configs")
    void testGetCurrentRewards() throws Exception {
        enqueue("""
                {"data": [{"condition_id": "0xc1", "rewards_max_spread": 0.03}], "next_cursor": "%s", "limit": 100, "count": 1}
                """.formatted(END_CURSOR));

        List<MarketReward> rewards = client.getCurrentRewards();

        assertEquals(1, rewards.size());
        assertEquals("0xc1", rewards.get(0).getConditionId());
        assertEquals(1, server.getRequestCount());
        assertEquals("/rewards/markets/current", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-IT-141: getRawRewardsForMarket(conditionId) paginates market-specific config")
    void testGetRawRewardsForMarket() throws Exception {
        enqueue("""
                {"data": [{"condition_id": "0xc1"}], "next_cursor": "%s", "limit": 100, "count": 1}
                """.formatted(END_CURSOR));

        List<MarketReward> rewards = client.getRawRewardsForMarket("0xc1");

        assertEquals(1, rewards.size());
        assertTrue(server.takeRequest().getPath().startsWith("/rewards/markets/0xc1"));
    }

    @Test
    @DisplayName("TC-IT-142: getEarningsForUserForDay(date) paginates and requires L2 auth")
    void testGetEarningsForUserForDay() throws Exception {
        enqueue("""
                {"data": [{"date": "2024-01-01", "earnings": 1.5}], "next_cursor": "%s", "limit": 100, "count": 1}
                """.formatted(END_CURSOR));

        List<UserEarning> earnings = client.getEarningsForUserForDay("2024-01-01");

        assertEquals(1, earnings.size());
        assertEquals(0, new java.math.BigDecimal("1.5").compareTo(earnings.get(0).getEarnings()));

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/rewards/user"));
        assertTrue(req.getPath().contains("date=2024-01-01"));
    }

    @Test
    @DisplayName("TC-IT-143: getTotalEarningsForUserForDay(date) returns the aggregated total")
    void testGetTotalEarningsForUserForDay() throws Exception {
        enqueue("[{\"date\": \"2024-01-01\", \"earnings\": 12.5}]");

        List<TotalUserEarning> totals = client.getTotalEarningsForUserForDay("2024-01-01");

        assertEquals(1, totals.size());
        assertEquals(0, new java.math.BigDecimal("12.5").compareTo(totals.get(0).getEarnings()));
        assertTrue(server.takeRequest().getPath().startsWith("/rewards/user/total"));
    }

    @Test
    @DisplayName("TC-IT-144: getUserEarningsAndMarketsConfig(date, ...) paginates and applies filters")
    void testGetUserEarningsAndMarketsConfig() throws Exception {
        enqueue("""
                {"data": [{"condition_id": "0xc1", "market_competitiveness": 0.8}], "next_cursor": "%s", "limit": 100, "count": 1}
                """.formatted(END_CURSOR));

        List<UserRewardsEarning> result =
                client.getUserEarningsAndMarketsConfig("2024-01-01", "earnings", "maker", true);

        assertEquals(1, result.size());
        assertEquals("0xc1", result.get(0).getConditionId());

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/rewards/user/markets"));
        assertTrue(req.getPath().contains("order_by=earnings"));
        assertTrue(req.getPath().contains("position=maker"));
        assertTrue(req.getPath().contains("no_competition=true"));
    }

    // -----------------------------------------------------------------------
    // Live activity
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-150: getMarketTradesEvents(conditionId) is an unauthenticated GET")
    void testGetMarketTradesEvents() throws Exception {
        enqueue("[{\"type\": \"trade\"}]");

        var events = client.getMarketTradesEvents("0xc1");

        assertEquals(1, events.size());
        assertTrue(server.takeRequest().getPath().startsWith("/live-activity/events/0xc1"));
    }
}
