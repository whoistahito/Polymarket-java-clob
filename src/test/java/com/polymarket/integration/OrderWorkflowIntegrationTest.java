package com.polymarket.integration;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.OrderBuilder;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.CreateOrderOptions;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderType;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.Side;
import com.polymarket.model.SignedOrder;
import com.polymarket.model.UserOrder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests covering the complete order lifecycle:
 * order creation (EIP-712 signing via {@link OrderBuilder}) → serialisation →
 * posting to the mock server → response deserialisation.
 *
 * <p>No real network calls are made.  The mock server accepts any valid JSON
 * body and replies with a canned {@link OrderResponse}.
 */
@DisplayName("Order Workflow Integration Tests")
class OrderWorkflowIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final ApiKeyCreds TEST_CREDS =
            new ApiKeyCreds("test-api-key-uuid", "c2VjcmV0MTIzNDU2Nzg=", "test-passphrase");
    private static final String TOKEN_ID =
            "71321045679252212594626385532706912750332728571942532289631379312455583992563";
    private static final String ORDER_RESPONSE_ID =
            "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab";

    private MockWebServer server;
    private PolymarketClient client;
    private OrderBuilder orderBuilder;

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

        Credentials creds = Credentials.create(TEST_PRIVATE_KEY);
        orderBuilder = new OrderBuilder(creds, 137);
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

    private String orderSuccessResponse(String status) {
        return """
                {
                  "success": true,
                  "orderID": "%s",
                  "status": "%s",
                  "makingAmount": "100000000",
                  "takingAmount": "200000000",
                  "errorMsg": ""
                }
                """.formatted(ORDER_RESPONSE_ID, status);
    }

    // -----------------------------------------------------------------------
    // TC-IT-201: Build a signed limit order and post it (status = live)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-201: buildOrder → postOrder → GTC order is placed live")
    void testBuildAndPostLimitOrder() throws Exception {
        enqueue(orderSuccessResponse("live"));

        // Build the EIP-712 signed order
        UserOrder userOrder = UserOrder.builder()
                .tokenID(TOKEN_ID)
                .side(Side.BUY)
                .price(new BigDecimal("0.50"))
                .size(new BigDecimal("10"))
                .feeRateBps(30)
                .build();

        CreateOrderOptions options = CreateOrderOptions.builder()
                .tickSize("0.01")
                .negRisk(false)
                .build();

        SignedOrder signed = orderBuilder.buildOrder(userOrder, options);
        assertNotNull(signed);
        assertNotNull(signed.signature());
        assertFalse(signed.signature().isBlank());

        // Build the posting payload
        PostOrderPayload payload = orderBuilder.buildPayload(
                signed, TEST_CREDS.getKey(), OrderType.GTC, false, false);
        assertNotNull(payload);

        // Post through the full client
        OrderResponse response = client.postOrder(payload);

        assertTrue(response.success());
        assertEquals(ORDER_RESPONSE_ID, response.orderID());
        assertEquals("live", response.status());
        assertEquals("100000000", response.makingAmount());
        assertEquals("200000000", response.takingAmount());

        // Verify the HTTP request was well-formed
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/order", req.getPath());
        assertNotNull(req.getHeader("POLY_API_KEY"));
        assertNotNull(req.getHeader("POLY_SIGNATURE"));

        // The request body must contain the signed order fields
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("order"));
        assertTrue(body.contains("orderType"));
    }

    // -----------------------------------------------------------------------
    // TC-IT-202: Post order — status = matched (immediate fill)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-202: postOrder returns 'matched' status when order fills immediately")
    void testPostOrderMatched() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                          "success": true,
                          "orderID": "%s",
                          "status": "matched",
                          "makingAmount": "100000000",
                          "takingAmount": "200000000",
                          "transactionsHashes": ["0xhash1"],
                          "tradeIDs": ["trade-abc"],
                          "errorMsg": ""
                        }
                        """.formatted(ORDER_RESPONSE_ID))
                .addHeader("Content-Type", "application/json"));

        UserOrder userOrder = UserOrder.builder()
                .tokenID(TOKEN_ID)
                .side(Side.SELL)
                .price(new BigDecimal("0.45"))
                .size(new BigDecimal("20"))
                .feeRateBps(30)
                .build();

        SignedOrder signed = orderBuilder.buildOrder(userOrder,
                CreateOrderOptions.builder().tickSize("0.01").negRisk(false).build());
        PostOrderPayload payload = orderBuilder.buildPayload(
                signed, TEST_CREDS.getKey(), OrderType.FOK, false, false);

        OrderResponse response = client.postOrder(payload);

        assertTrue(response.success());
        assertEquals("matched", response.status());
        assertNotNull(response.transactionsHashes());
        assertEquals(1, response.transactionsHashes().size());
    }

    // -----------------------------------------------------------------------
    // TC-IT-203: postOrder convenience overload (SignedOrder, OrderType, ...)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-203: postOrder(SignedOrder, GTC, false, false) builds payload internally")
    void testPostOrderConvenienceOverload() throws Exception {
        enqueue(orderSuccessResponse("live"));

        UserOrder userOrder = UserOrder.builder()
                .tokenID(TOKEN_ID)
                .side(Side.BUY)
                .price(new BigDecimal("0.40"))
                .size(new BigDecimal("5"))
                .feeRateBps(30)
                .build();

        SignedOrder signed = orderBuilder.buildOrder(userOrder,
                CreateOrderOptions.builder().tickSize("0.01").negRisk(false).build());

        // Use the convenience overload directly on the client
        OrderResponse response = client.postOrder(signed, OrderType.GTC, false, false);

        assertTrue(response.success());
        assertEquals("live", response.status());
    }

    // -----------------------------------------------------------------------
    // TC-IT-204: postOrders (batch) — two orders
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-204: postOrders(List<PostOrderPayload>) sends and deserialises batch response")
    void testPostBatchOrders() throws Exception {
        enqueue("""
                [
                  {"success": true, "orderID": "0xAAA", "status": "live",
                   "makingAmount": "50000000", "takingAmount": "100000000", "errorMsg": ""},
                  {"success": true, "orderID": "0xBBB", "status": "live",
                   "makingAmount": "60000000", "takingAmount": "120000000", "errorMsg": ""}
                ]
                """);

        SignedOrder so1 = orderBuilder.buildOrder(
                UserOrder.builder().tokenID(TOKEN_ID).side(Side.BUY)
                        .price(new BigDecimal("0.50")).size(new BigDecimal("5")).feeRateBps(30).build(),
                CreateOrderOptions.builder().tickSize("0.01").negRisk(false).build());

        SignedOrder so2 = orderBuilder.buildOrder(
                UserOrder.builder().tokenID(TOKEN_ID).side(Side.BUY)
                        .price(new BigDecimal("0.51")).size(new BigDecimal("6")).feeRateBps(30).build(),
                CreateOrderOptions.builder().tickSize("0.01").negRisk(false).build());

        PostOrderPayload p1 = orderBuilder.buildPayload(so1, TEST_CREDS.getKey(), OrderType.GTC, false, false);
        PostOrderPayload p2 = orderBuilder.buildPayload(so2, TEST_CREDS.getKey(), OrderType.GTC, false, false);

        List<OrderResponse> responses = client.postOrders(List.of(p1, p2));

        assertEquals(2, responses.size());
        assertEquals("0xAAA", responses.get(0).orderID());
        assertEquals("0xBBB", responses.get(1).orderID());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/orders", req.getPath());
    }

    // -----------------------------------------------------------------------
    // TC-IT-205: createAndPostOrder — auto-fetches tick/fee/negRisk
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-205: createAndPostOrder() fetches metadata then posts the order")
    void testCreateAndPostOrder() throws Exception {
        // createAndPostOrder fetches: tick-size, fee-rate, neg-risk, then posts
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");   // getTickSize
        enqueue("{\"fee_rate_bps\": 30}");                          // getFeeRateBps
        enqueue("{\"neg_risk\": false}");                           // getNegRisk
        enqueue(orderSuccessResponse("live"));                      // postOrder

        OrderResponse response = client.createAndPostOrder(
                TOKEN_ID, Side.BUY, new BigDecimal("0.50"), new BigDecimal("10"), OrderType.GTC);

        assertTrue(response.success());
        assertEquals(ORDER_RESPONSE_ID, response.orderID());
        assertEquals(4, server.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // TC-IT-206: Signed order fields are EIP-712 compatible
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-206: SignedOrder from OrderBuilder has expected structural fields")
    void testSignedOrderStructure() throws Exception {
        UserOrder userOrder = UserOrder.builder()
                .tokenID(TOKEN_ID)
                .side(Side.BUY)
                .price(new BigDecimal("0.50"))
                .size(new BigDecimal("100"))
                .feeRateBps(30)
                .build();

        SignedOrder signed = orderBuilder.buildOrder(userOrder,
                CreateOrderOptions.builder().tickSize("0.01").negRisk(false).build());

        // Structural checks
        assertNotNull(signed.tokenId());
        assertNotNull(signed.makerAmount());
        assertNotNull(signed.takerAmount());
        assertNotNull(signed.signature());
        assertNotNull(signed.side());
        assertFalse(signed.signature().isBlank());

        // Signature must be a 65-byte hex string (130 hex chars + "0x" prefix = 132 chars)
        assertTrue(signed.signature().startsWith("0x"));
        assertEquals(132, signed.signature().length());

        // Salt must be within IEEE 754 safe integer range
        long salt = signed.salt();
        assertTrue(salt >= 0 && salt <= (1L << 53) - 1,
                "Salt " + salt + " is outside IEEE 754 safe integer range");
    }

    // -----------------------------------------------------------------------
    // TC-IT-207: createAndPostMarketOrder (FAK)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-IT-207: createAndPostMarketOrder() fetches metadata and posts FAK order")
    void testCreateAndPostMarketOrder() throws Exception {
        enqueue("{\"version\": 2}");
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");
        enqueue("{\"neg_risk\": false}");
        enqueue("{\"fee_rate_bps\": 30}");
        enqueue("""
                {
                  "market":"0xmarket", "asset_id":"%s", "timestamp":"1", "hash":"h",
                  "bids":[], "asks":[{"price":"0.50","size":"500"}],
                  "min_order_size":"5", "tick_size":"0.01", "neg_risk":false,
                  "last_trade_price":"0.50"
                }
                """.formatted(TOKEN_ID));
        enqueue(orderSuccessResponse("matched"));

        OrderResponse response = client.createAndPostMarketOrder(
                TOKEN_ID, Side.BUY, new BigDecimal("100"), OrderType.FAK);

        assertTrue(response.success());
        assertEquals(ORDER_RESPONSE_ID, response.orderID());
        assertEquals("matched", response.status());
    }

    @Test
    @DisplayName("TC-IT-208: single order overload sends postOnly")
    void testCreateAndPostSinglePostOnlyOrder() throws Exception {
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");
        enqueue("{\"fee_rate_bps\": 30}");
        enqueue("{\"neg_risk\": false}");
        enqueue(orderSuccessResponse("live"));

        OrderResponse response = client.createAndPostOrder(
                TOKEN_ID, Side.SELL, new BigDecimal("0.50"), new BigDecimal("6.66"),
                OrderType.GTC, true, false);

        assertTrue(response.success());
        RecordedRequest request = null;
        for (int i = 0; i < 4; i++) request = server.takeRequest();
        assertNotNull(request);
        assertTrue(request.getBody().readUtf8().contains("\"postOnly\":true"));
    }
}
