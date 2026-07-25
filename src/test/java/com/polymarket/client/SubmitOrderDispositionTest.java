package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.OrderSubmission;
import com.polymarket.model.OrderSubmissionStatus;
import com.polymarket.model.OrderType;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.Side;
import com.polymarket.model.SignatureType;
import com.polymarket.model.SignedOrder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 022 — {@code submitOrder} never throws for an exchange-side outcome; it classifies.
 *
 * <p>Exercised end to end over {@link MockWebServer} so the HTTP layer's own error handling is part
 * of what is being pinned.
 */
@DisplayName("TC-SOD — submitOrder disposition over HTTP (Ticket 022)")
class SubmitOrderDispositionTest {

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
            .apiCreds(new ApiKeyCreds("test-key", "c2VjcmV0", "pass123"))
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static PostOrderPayload payload() {
        SignedOrder order = SignedOrder.builder()
            .salt(1L)
            .maker("0x0000000000000000000000000000000000000001")
            .signer("0x0000000000000000000000000000000000000001")
            .taker("0x0000000000000000000000000000000000000000")
            .tokenId("tok1")
            .makerAmount("5000000")
            .takerAmount("10000000")
            .expiration("0")
            .nonce("0")
            .feeRateBps("0")
            .side(Side.SELL)
            .signatureType(SignatureType.EOA)
            .signature("0xdeadbeef")
            .build();
        return PostOrderPayload.builder()
            .order(order)
            .owner("test-key")
            .orderType(OrderType.FAK)
            .deferExec(false)
            .build();
    }

    private void enqueue(int code, String body) {
        server.enqueue(new MockResponse()
            .setResponseCode(code)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("TC-SOD-001 a valid matched FAK is ACCEPTED")
    void acceptedFak() {
        enqueue(200, "{\"success\":true,\"orderID\":\"0xabc\",\"status\":\"matched\"}");

        OrderSubmission submission = client.submitOrder(payload());

        assertEquals(OrderSubmissionStatus.ACCEPTED, submission.status());
        assertEquals("0xabc", submission.orderId());
        assertEquals(200, submission.httpStatus());
    }

    @Test
    @DisplayName("TC-SOD-002 a no-match FAK 400 is REJECTED with the body preserved")
    void rejectedNoMatchFak() {
        enqueue(400, "{\"error\":\"no orders found to match with FAK order.\"}");

        OrderSubmission submission = client.submitOrder(payload());

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertFalse(submission.isSafeToRetry());
        assertEquals(400, submission.httpStatus());
        assertTrue(submission.responseBody().contains("no orders found to match"));
    }

    @Test
    @DisplayName("TC-SOD-003 the documented 500 timeout is REJECTED and safe to retry")
    void rejectedDocumentedTimeout() {
        enqueue(500, "{\"error\":\"order timed out\"}");

        OrderSubmission submission = client.submitOrder(payload());

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertTrue(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-SOD-004 a generic 500 is UNKNOWN, not a rejection")
    void unknownGenericServerError() {
        enqueue(500, "{\"error\":\"internal server error\"}");

        OrderSubmission submission = client.submitOrder(payload());

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertFalse(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-SOD-005 a lost connection is UNKNOWN")
    void unknownDisconnect() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        OrderSubmission submission = client.submitOrder(payload());

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertNotNull(submission.failure());
        assertNull(submission.orderId());
    }

    @Test
    @DisplayName("TC-SOD-006 a 2xx success without an order ID is UNKNOWN")
    void unknownBlankOrderId() {
        enqueue(200, "{\"success\":true,\"orderID\":\"\",\"status\":\"matched\"}");

        assertEquals(OrderSubmissionStatus.UNKNOWN, client.submitOrder(payload()).status());
    }

    @Test
    @DisplayName("TC-SOD-007 a 2xx success carrying an error message is UNKNOWN")
    void unknownSuccessPlusError() {
        enqueue(200,
            "{\"success\":true,\"orderID\":\"0xabc\",\"status\":\"matched\",\"errorMsg\":\"order timed out\"}");

        assertEquals(OrderSubmissionStatus.UNKNOWN, client.submitOrder(payload()).status());
    }

    @Test
    @DisplayName("TC-SOD-008 a 2xx body reporting success=false is REJECTED")
    void rejectedExplicitFailure() {
        enqueue(200, "{\"success\":false,\"errorMsg\":\"not enough balance / allowance\"}");

        OrderSubmission submission = client.submitOrder(payload());
        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertEquals("not enough balance / allowance", submission.errorMessage());
    }

    @Test
    @DisplayName("TC-SOD-009 an unparseable 2xx body is UNKNOWN, never accepted")
    void unknownUnparseableBody() {
        enqueue(200, "not json at all");

        assertEquals(OrderSubmissionStatus.UNKNOWN, client.submitOrder(payload()).status());
    }

    @Test
    @DisplayName("TC-SOD-010 the async wrapper completes through the future with the same disposition")
    void asyncCompletesThroughFuture() throws Exception {
        enqueue(400, "{\"error\":\"no orders found to match with FAK order.\"}");

        OrderSubmission submission =
            AsyncPolymarketClient.wrap(client).submitOrder(payload()).get(10, TimeUnit.SECONDS);

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertEquals(400, submission.httpStatus());
    }

    @Test
    @DisplayName("TC-SOD-011 the async wrapper does not fail the future on transport loss")
    void asyncTransportLossIsNotAFailedFuture() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        OrderSubmission submission =
            AsyncPolymarketClient.wrap(client).submitOrder(payload()).get(10, TimeUnit.SECONDS);

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
    }

    @Test
    @DisplayName("TC-SOD-012 postOrder keeps throwing, so existing callers are unaffected")
    void legacyPostOrderStillThrows() {
        enqueue(400, "{\"error\":\"no orders found to match with FAK order.\"}");

        org.junit.jupiter.api.Assertions.assertThrows(
            HttpStatusException.class, () -> client.postOrder(payload()));
    }
}
