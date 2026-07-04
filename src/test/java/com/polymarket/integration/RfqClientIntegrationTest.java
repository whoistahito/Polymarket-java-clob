package com.polymarket.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.PolymarketClient;
import com.polymarket.client.RfqClient;
import com.polymarket.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RfqClient against the CLOB RFQ endpoints (maker/quote and
 * requester/request APIs, per docs.polymarket.com/api-reference/maker). Uses
 * {@link MockWebServer} so no real network call is ever made.
 */
@DisplayName("RfqClient Integration Tests")
class RfqClientIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final ApiKeyCreds TEST_CREDS =
            new ApiKeyCreds("test-api-key-uuid", "c2VjcmV0MTIzNDU2Nzg=", "test-passphrase");
    private static final String TOKEN_ID = "123456789";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private PolymarketClient client;
    private RfqClient rfq;

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
        rfq = client.rfq();
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyAsMap(RecordedRequest request) throws Exception {
        return MAPPER.readValue(request.getBody().readUtf8(), Map.class);
    }

    // -----------------------------------------------------------------------
    // Request lifecycle: create / cancel / list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-RFQ-IT-001: createRfqRequest posts assetIn/assetOut/amountIn/amountOut derived from side")
    void testCreateRfqRequestBuy() throws Exception {
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");
        enqueue("{\"requestId\": \"req-abc\"}");

        RfqUserOrder order = RfqUserOrder.builder()
                .tokenID(TOKEN_ID)
                .side(Side.BUY)
                .price(new BigDecimal("0.5"))
                .size(new BigDecimal("100"))
                .build();

        RfqRequestResponse response = rfq.createRfqRequest(order, null);

        assertEquals("req-abc", response.getRequestId());

        server.takeRequest(); // tick-size lookup
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/rfq/request", req.getPath());

        Map<String, Object> body = bodyAsMap(req);
        assertEquals(TOKEN_ID, body.get("assetIn"));
        assertEquals("0", body.get("assetOut"));
        assertEquals("100000000", body.get("amountIn")); // 100 shares * 1e6
        assertEquals("50000000", body.get("amountOut"));  // 100 * 0.5 * 1e6
    }

    @Test
    @DisplayName("TC-RFQ-IT-002: createRfqRequest without API creds throws before any network call")
    void testCreateRfqRequestRequiresAuth() {
        PolymarketClient unauthenticated =
                new PolymarketClient.Builder()
                        .privateKey(TEST_PRIVATE_KEY)
                        .clobHost(server.url("").toString())
                        .build();

        assertThrows(IllegalStateException.class, () ->
                unauthenticated.rfq().createRfqRequest(
                        RfqUserOrder.builder().tokenID(TOKEN_ID).side(Side.BUY)
                                .price(new BigDecimal("0.5")).size(new BigDecimal("1")).build(),
                        "0.01"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RFQ-IT-003: cancelRfqRequest sends a DELETE with the request id")
    void testCancelRfqRequest() throws Exception {
        enqueue("{}");

        rfq.cancelRfqRequest(CancelRfqRequestParams.builder().requestId("req-abc").build());

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/rfq/request", req.getPath());
        assertEquals("req-abc", bodyAsMap(req).get("requestId"));
    }

    @Test
    @DisplayName("TC-RFQ-IT-004: getRfqRequests builds a query string and deserialises the paginated response")
    void testGetRfqRequests() throws Exception {
        enqueue("""
                {
                  "data": [{"requestId": "req-1", "token": "%s", "side": "BUY"}],
                  "next_cursor": "c1",
                  "limit": 10,
                  "count": 1
                }
                """.formatted(TOKEN_ID));

        RfqPaginatedResponse<RfqRequest> response =
                rfq.getRfqRequests(GetRfqRequestsParams.builder().state("open").limit(10).build());

        assertEquals(1, response.getData().size());
        assertEquals("req-1", response.getData().get(0).getRequestId());
        assertEquals("c1", response.getNextCursor());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().startsWith("/rfq/data/requests"));
        assertTrue(req.getPath().contains("state=open"));
        assertTrue(req.getPath().contains("limit=10"));
    }

    // -----------------------------------------------------------------------
    // Quote lifecycle: create / cancel / list / best
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-RFQ-IT-010: createRfqQuote (SELL) posts assetIn=USDC(0), assetOut=token")
    void testCreateRfqQuoteSell() throws Exception {
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");
        enqueue("{\"quoteId\": \"q-1\"}");

        RfqUserQuote quote = RfqUserQuote.builder()
                .requestId("req-1")
                .tokenID(TOKEN_ID)
                .side(Side.SELL)
                .price(new BigDecimal("0.4"))
                .size(new BigDecimal("50"))
                .build();

        RfqQuoteResponse response = rfq.createRfqQuote(quote, null);

        assertEquals("q-1", response.getQuoteId());

        server.takeRequest(); // tick-size lookup
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/rfq/quote", req.getPath());

        Map<String, Object> body = bodyAsMap(req);
        assertEquals("req-1", body.get("requestId"));
        assertEquals("0", body.get("assetIn"));
        assertEquals("20000000", body.get("amountIn"));  // 50 * 0.4 * 1e6
        assertEquals("50000000", body.get("amountOut")); // 50 * 1e6
    }

    @Test
    @DisplayName("TC-RFQ-IT-011: cancelRfqQuote sends a DELETE with the quote id")
    void testCancelRfqQuote() throws Exception {
        enqueue("{}");

        rfq.cancelRfqQuote(CancelRfqQuoteParams.builder().quoteId("q-1").build());

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/rfq/quote", req.getPath());
        assertEquals("q-1", bodyAsMap(req).get("quoteId"));
    }

    @Test
    @DisplayName("TC-RFQ-IT-012: getRfqRequesterQuotes and getRfqQuoterQuotes hit distinct endpoints")
    void testGetQuotesEndpointsAreDistinct() throws Exception {
        enqueue("{\"data\": [], \"limit\": 10, \"count\": 0}");
        enqueue("{\"data\": [], \"limit\": 10, \"count\": 0}");

        rfq.getRfqRequesterQuotes(GetRfqQuotesParams.builder().build());
        rfq.getRfqQuoterQuotes(GetRfqQuotesParams.builder().build());

        assertEquals("/rfq/data/requester/quotes", server.takeRequest().getPath());
        assertEquals("/rfq/data/quoter/quotes", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RFQ-IT-013: getRfqBestQuote requests by requestId and deserialises the quote")
    void testGetRfqBestQuote() throws Exception {
        enqueue("""
                {"quoteId": "q-best", "requestId": "req-1", "token": "%s", "price": 0.42}
                """.formatted(TOKEN_ID));

        RfqQuote best = rfq.getRfqBestQuote(GetRfqBestQuoteParams.builder().requestId("req-1").build());

        assertEquals("q-best", best.getQuoteId());
        assertEquals(0.42, best.getPrice(), 1e-9);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("requestId=req-1"));
    }

    @Test
    @DisplayName("TC-RFQ-IT-014: rfqConfig fetches the raw RFQ configuration")
    void testRfqConfig() throws Exception {
        enqueue("{\"minSizeUsdc\": \"1\", \"maxQuoteExpirySec\": 30}");

        Map<String, Object> config = rfq.rfqConfig();

        assertEquals("1", config.get("minSizeUsdc"));
        assertEquals(30, config.get("maxQuoteExpirySec"));
        assertEquals("/rfq/config", server.takeRequest().getPath());
    }

    // -----------------------------------------------------------------------
    // Full accept / approve flow (quote lookup -> order build -> submit)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-RFQ-IT-020: acceptRfqQuote on a COMPLEMENTARY match builds the opposite-side order")
    void testAcceptRfqQuoteComplementary() throws Exception {
        // 1) quote lookup (requester view)
        enqueue("""
                {
                  "data": [{
                    "quoteId": "q-1", "requestId": "req-1", "token": "%s",
                    "side": "BUY", "sizeIn": "100", "sizeOut": "50",
                    "price": 0.5, "matchType": "COMPLEMENTARY"
                  }],
                  "limit": 10, "count": 1
                }
                """.formatted(TOKEN_ID));
        // 2) tick size, 3) neg risk, 4) version, 5) fee rate — all needed by createOrder()
        enqueue("{\"tick_size\": \"0.01\", \"minimum\": 0.01}");
        enqueue("{\"neg_risk\": false}");
        enqueue("{\"version\": 2}");
        enqueue("{\"base_fee\": 0}");
        // 6) accept endpoint
        enqueue("{}");

        rfq.acceptRfqQuote(AcceptQuoteParams.builder()
                .requestId("req-1")
                .quoteId("q-1")
                .expiration(1_800_000_000L)
                .build());

        for (int i = 0; i < 5; i++) {
            server.takeRequest();
        }
        RecordedRequest acceptReq = server.takeRequest();
        assertEquals("/rfq/request/accept", acceptReq.getPath());

        Map<String, Object> body = bodyAsMap(acceptReq);
        // quote side BUY + COMPLEMENTARY -> taker order is SELL
        assertEquals("SELL", body.get("side"));
        assertEquals("req-1", body.get("requestId"));
        assertEquals("q-1", body.get("quoteId"));
        assertEquals(TOKEN_ID, body.get("tokenId"));
    }

    @Test
    @DisplayName("TC-RFQ-IT-021: approveRfqOrder without API creds throws before any network call")
    void testApproveRfqOrderRequiresAuth() {
        PolymarketClient unauthenticated =
                new PolymarketClient.Builder()
                        .privateKey(TEST_PRIVATE_KEY)
                        .clobHost(server.url("").toString())
                        .build();

        assertThrows(IllegalStateException.class, () ->
                unauthenticated.rfq().approveRfqOrder(
                        ApproveOrderParams.builder().requestId("r").quoteId("q").expiration(1L).build()));
        assertEquals(0, server.getRequestCount());
    }
}
