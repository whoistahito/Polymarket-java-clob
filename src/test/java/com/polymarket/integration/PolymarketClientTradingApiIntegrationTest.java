package com.polymarket.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.L2HmacSigner;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.AssetType;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.HeartbeatResponse;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.SignatureType;
import com.polymarket.model.Trade;
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
 * Integration tests for the L2-authenticated (trading) endpoints of
 * {@link PolymarketClient}.
 *
 * <p>A valid-looking {@link ApiKeyCreds} is injected so that the L2 auth guard
 * passes.  The HMAC signature itself is accepted by the mock server because it
 * does not validate it — the tests focus on correct request construction and
 * response deserialisation.
 */
@DisplayName("PolymarketClient Trading API Integration Tests")
class PolymarketClientTradingApiIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    /** Base64-encoded "secret" — must be valid base64 for HMAC-SHA256 to decode it. */
    private static final ApiKeyCreds TEST_CREDS =
            new ApiKeyCreds("test-api-key-uuid", "c2VjcmV0MTIzNDU2Nzg=", "test-passphrase");

    private static final String TOKEN_ID =
            "71321045679252212594626385532706912750332728571942532289631379312455583992563";
    private static final String ORDER_ID =
            "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab";

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
    // TC-IT-101: getOpenOrders (no filter)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-101: getOpenOrders() deserialises list of OpenOrder")
    void testGetOpenOrders() throws Exception {
        enqueue("""
                [
                  {
                    "id": "%s",
                    "status": "LIVE",
                    "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
                    "maker_address": "0x1234567890123456789012345678901234567890",
                    "market": "0x0000000000000000000000000000000000000000000000000000000000000001",
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
                """.formatted(ORDER_ID, TOKEN_ID));

        List<OpenOrder> orders = client.getOpenOrders();

        assertNotNull(orders);
        assertEquals(1, orders.size());

        OpenOrder order = orders.get(0);
        assertEquals(ORDER_ID, order.getId());
        assertEquals(TOKEN_ID, order.getAssetId());
        assertEquals("BUY", order.getSide());
        assertEquals("0.5", order.getPrice());
        assertEquals("GTC", order.getOrderType());
        assertEquals(1700000000L, order.getCreatedAt());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().startsWith("/data/orders"));
        // L2 auth headers must be present
        assertNotNull(req.getHeader("POLY_API_KEY"));
    assertNotNull(req.getHeader("POLY_ADDRESS"));
        assertNotNull(req.getHeader("POLY_SIGNATURE"));
        assertNotNull(req.getHeader("POLY_TIMESTAMP"));
        assertNotNull(req.getHeader("POLY_PASSPHRASE"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-102: getOpenOrders with typed OpenOrderParams filter
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-102: getOpenOrders(OpenOrderParams) passes query params and deserialises")
    void testGetOpenOrdersWithParams() throws Exception {
        enqueue("[]");

        var params = com.polymarket.model.OpenOrderParams.builder()
                .assetId(TOKEN_ID)
                .build();

        List<OpenOrder> orders = client.getOpenOrders(params);

        assertNotNull(orders);
        assertTrue(orders.isEmpty());

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("asset_id=" + TOKEN_ID));
    }

    // -----------------------------------------------------------------------
    // TC-IT-103: getOrder (single by ID)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-103: getOrder(orderId) deserialises a single OpenOrder")
    void testGetOrder() throws Exception {
        enqueue("""
                {
                  "id": "%s",
                  "status": "LIVE",
                  "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
                  "maker_address": "0x1234567890123456789012345678901234567890",
                  "market": "0x0001",
                  "asset_id": "%s",
                  "side": "SELL",
                  "original_size": "200000000",
                  "size_matched": "50000000",
                  "price": "0.75",
                  "outcome": "NO",
                  "expiration": "1735689600",
                  "order_type": "GTC",
                  "associate_trades": ["trade-123"],
                  "created_at": 1700000001
                }
                """.formatted(ORDER_ID, TOKEN_ID));

        OpenOrder order = client.getOrder(ORDER_ID);

        assertNotNull(order);
        assertEquals(ORDER_ID, order.getId());
        assertEquals("SELL", order.getSide());
        assertEquals("0.75", order.getPrice());
        assertEquals(1, order.getAssociateTrades().size());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains(ORDER_ID));
    }

    // -----------------------------------------------------------------------
    // TC-IT-104: getTrades (auto-paginated, single page)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-104: getTrades() auto-paginates and returns a flat list of Trade")
    void testGetTrades() throws Exception {
        // Single page — next_cursor = END_CURSOR stops iteration
        enqueue("""
                {
                  "limit": 100,
                  "next_cursor": "LTE=",
                  "count": 1,
                  "data": [
                    {
                      "id": "trade-001",
                      "taker_order_id": "%s",
                      "market": "0x0001",
                      "asset_id": "%s",
                      "side": "BUY",
                      "size": "100000000",
                      "fee_rate_bps": "30",
                      "price": "0.5",
                      "status": "CONFIRMED",
                      "match_time": "1700000010",
                      "last_update": "1700000010",
                      "outcome": "YES",
                      "trader_side": "TAKER"
                    }
                  ]
                }
                """.formatted(ORDER_ID, TOKEN_ID));

        List<Trade> trades = client.getTrades();

        assertNotNull(trades);
        assertEquals(1, trades.size());

        Trade t = trades.get(0);
        assertEquals("trade-001", t.getId());
        assertEquals("0.5", t.getPrice());
    }

    // -----------------------------------------------------------------------
    // TC-IT-105: getTrades auto-pagination across two pages
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-105: getTrades() concatenates results across multiple pages")
    void testGetTradesMultiPage() throws Exception {
        // Page 1 — cursor advances
        enqueue("""
                {
                  "limit": 100,
                  "next_cursor": "MTAw",
                  "count": 1,
                  "data": [{"id": "trade-page1", "taker_order_id": "0xT1",
                    "market": "0xM", "asset_id": "A", "side": "BUY",
                    "size": "1", "fee_rate_bps": "30", "price": "0.5",
                    "status": "CONFIRMED", "match_time": "1", "last_update": "1",
                    "outcome": "YES", "trader_side": "TAKER"}]
                }
                """);
        // Page 2 — end cursor stops loop
        enqueue("""
                {
                  "limit": 100,
                  "next_cursor": "LTE=",
                  "count": 1,
                  "data": [{"id": "trade-page2", "taker_order_id": "0xT2",
                    "market": "0xM", "asset_id": "A", "side": "SELL",
                    "size": "2", "fee_rate_bps": "30", "price": "0.6",
                    "status": "CONFIRMED", "match_time": "2", "last_update": "2",
                    "outcome": "NO", "trader_side": "MAKER"}]
                }
                """);

        List<Trade> trades = client.getTrades();

        assertEquals(2, trades.size());
        assertEquals("trade-page1", trades.get(0).getId());
        assertEquals("trade-page2", trades.get(1).getId());
        assertEquals(2, server.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // TC-IT-106: postHeartbeat
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-106: postHeartbeat() sends the heartbeat_id and returns response")
    void testPostHeartbeat() throws Exception {
        enqueue("{\"heartbeat_id\": \"hb-uuid-001\", \"error\": null}");

        HeartbeatResponse resp = client.postHeartbeat("hb-uuid-001");

        assertNotNull(resp);
        assertEquals("hb-uuid-001", resp.getHeartbeatId());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/heartbeats"));
        assertTrue(req.getBody().readUtf8().contains("hb-uuid-001"));
        assertNotNull(req.getHeader("POLY_API_KEY"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-107: cancelOrder
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-107: cancelOrder(orderId) sends DELETE with body and parses response")
    void testCancelOrder() throws Exception {
        enqueue("""
                {"canceled": ["%s"], "not_canceled": {}}
                """.formatted(ORDER_ID));

        Map<String, Object> result = client.cancelOrder(ORDER_ID);

        assertNotNull(result);
        assertTrue(result.containsKey("canceled"));

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/order", req.getPath());
        assertTrue(req.getBody().readUtf8().contains(ORDER_ID));
    }

    // -----------------------------------------------------------------------
    // TC-IT-108: cancelAll
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-108: cancelAll() sends DELETE to /cancel-all")
    void testCancelAll() throws Exception {
        enqueue("{\"canceled\": [], \"not_canceled\": {}}");

        Map<String, Object> result = client.cancelAll();

        assertNotNull(result);
        assertTrue(result.containsKey("canceled"));

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/cancel-all", req.getPath());
        assertNotNull(req.getHeader("POLY_API_KEY"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-109: getBalanceAllowance
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-109: getBalanceAllowance() deserialises balance and allowance")
    void testGetBalanceAllowance() throws Exception {
        enqueue("{\"balance\": \"1000000000\", \"allowance\": \"500000000\"}");

        BalanceAllowanceParams params = BalanceAllowanceParams.builder()
                .tokenId(TOKEN_ID)
                .assetType(AssetType.CONDITIONAL)
                .build();

        BalanceAllowanceResponse resp = client.getBalanceAllowance(params);

        assertNotNull(resp);
        assertEquals("1000000000", resp.getBalance());
        assertEquals("500000000", resp.getAllowance());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().startsWith("/balance-allowance"));
    assertNotNull(req.getHeader("POLY_ADDRESS"));

    long timestamp = Long.parseLong(req.getHeader("POLY_TIMESTAMP"));
    String expectedSignature =
        new L2HmacSigner()
            .sign(TEST_CREDS.getSecret(), timestamp, "GET", "/balance-allowance", null);
    assertEquals(expectedSignature, req.getHeader("POLY_SIGNATURE"));
  }

  @Test
  @DisplayName(
      "TC-IT-109b: getBalanceAllowance() includes signature_type and funder from client config")
  void testGetBalanceAllowanceIncludesSignatureTypeAndFunder() throws Exception {
    enqueue("{\"balance\": \"61000000\", \"allowance\": \"61000000\"}");

    String mockBase = server.url("").toString().replaceAll("/$", "");
    String funder = "0x1111111111111111111111111111111111111111";
    PolymarketClient proxyClient =
        new PolymarketClient.Builder()
            .privateKey(TEST_PRIVATE_KEY)
            .chainId(137)
            .clobHost(mockBase)
            .apiCreds(TEST_CREDS)
            .signatureType(SignatureType.POLY_GNOSIS_SAFE)
            .funderAddress(funder)
            .build();

    BalanceAllowanceResponse resp =
        proxyClient.getBalanceAllowance(
            BalanceAllowanceParams.builder().assetType(AssetType.COLLATERAL).build());

    assertNotNull(resp);
    assertEquals("61000000", resp.getBalance());

    RecordedRequest req = server.takeRequest();
    assertEquals("GET", req.getMethod());
    assertTrue(req.getPath().startsWith("/balance-allowance"));
    assertTrue(req.getPath().contains("asset_type=COLLATERAL"));
    assertTrue(req.getPath().contains("signature_type=2"));
    assertTrue(req.getPath().contains("funder=" + funder));
    }

    // -----------------------------------------------------------------------
    // TC-IT-110: requireL2Auth guard fires without credentials
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-110: L2 auth guard throws IllegalStateException when no API creds")
    void testL2AuthGuardFires() {
        String mockBase = server.url("").toString().replaceAll("/$", "");
        PolymarketClient unauthClient = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .clobHost(mockBase)
                .build();

        assertThrows(IllegalStateException.class, unauthClient::getOpenOrders);
        assertThrows(IllegalStateException.class, unauthClient::cancelAll);
        assertThrows(IllegalStateException.class, unauthClient::getTrades);
        // No request should have reached the server
        assertEquals(0, server.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // TC-IT-111: cancelOrders (batch cancel)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-111: cancelOrders(List) sends DELETE /orders with JSON array body")
    void testCancelOrders() throws Exception {
        String id2 = "0xfedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210fe";
        enqueue("""
                {"canceled": ["%s", "%s"], "not_canceled": {}}
                """.formatted(ORDER_ID, id2));

        Map<String, Object> result = client.cancelOrders(List.of(ORDER_ID, id2));

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<String> canceled = (List<String>) result.get("canceled");
        assertEquals(2, canceled.size());

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/orders", req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains(ORDER_ID));
        assertTrue(body.contains(id2));
    }
}
