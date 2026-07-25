package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.OrderSubmission;
import com.polymarket.model.OrderType;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.Side;
import com.polymarket.model.SignatureType;
import com.polymarket.model.SignedOrder;
import java.io.IOException;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 035 — {@code POST /order} is not idempotent, so a lost response must never be transparently
 * replayed by the SDK's own HTTP layer. OkHttp's {@code retryOnConnectionFailure(true)} does exactly
 * that: after a broken connection it silently resends the identical request on a new one, so
 * {@code call.execute()} can return a normal response while having sent the order body twice.
 *
 * <p>Order placement must therefore run on a client with that behaviour disabled. Read paths
 * (GET, and non-order POSTs) may keep it, because repeating a lost read is safe.
 */
@DisplayName("TC-HCP — order-submission client is exempt from connection-failure replay (Ticket 035)")
class HttpClientNoReplayTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("TC-HCP-001 the default HttpClient retries on connection failure (read paths)")
    void defaultClientRetriesOnConnectionFailure() {
        HttpClient client = new HttpClient();

        assertTrue(client.okHttpClient().retryOnConnectionFailure());
    }

    @Test
    @DisplayName("TC-HCP-002 withoutConnectionFailureRetry() disables OkHttp's own replay")
    void derivedClientDoesNotRetryOnConnectionFailure() {
        HttpClient client = new HttpClient();

        HttpClient noReplay = client.withoutConnectionFailureRetry();

        assertFalse(noReplay.okHttpClient().retryOnConnectionFailure());
        assertTrue(
            client.okHttpClient().retryOnConnectionFailure(),
            "deriving a no-replay client must not mutate the original read client");
    }

    @Test
    @DisplayName("TC-HCP-003 the derived client keeps the same connection pool, proxy, and mapper")
    void derivedClientSharesConfiguration() {
        ProxyConfig proxy = new ProxyConfig("proxy.example.com", 8080);
        HttpClient client = new HttpClient.Builder().proxy(proxy).build();

        HttpClient noReplay = client.withoutConnectionFailureRetry();

        assertEquals(proxy, noReplay.proxyConfig());
        assertSame(
            client.okHttpClient().connectionPool(),
            noReplay.okHttpClient().connectionPool(),
            "connections should still be pooled together, only the replay behaviour differs");
        assertSame(client.objectMapper(), noReplay.objectMapper());
    }

    @Test
    @DisplayName("TC-HCP-004 PolymarketClient routes order placement through the no-replay client")
    void polymarketClientOrderPathUsesNoReplayClient() {
        PolymarketClient client =
            new PolymarketClient.Builder()
                .privateKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80")
                .apiCreds(new ApiKeyCreds("test-key", "c2VjcmV0", "pass123"))
                .build();

        assertFalse(
            client.getOrderHttp().okHttpClient().retryOnConnectionFailure(),
            "POST /order must not run on a client that can silently replay it");
        assertTrue(
            client.getHttp().okHttpClient().retryOnConnectionFailure(),
            "read paths keep connection-failure retry, per Ticket 035");
    }

    @Test
    @DisplayName(
        "TC-HCP-005 the derived client's app-level retry budget is 0, never inherited from the parent")
    void derivedClientDoesNotInheritAppLevelMaxRetries() throws IOException {
        // A parent built for READ resilience with maxRetries(3): HttpClient#executeToString retries
        // on any IOException and on retryable statuses (425/429/5xx) for ANY method, POST included.
        // If withoutConnectionFailureRetry() inherited this value, raising it for reads would
        // silently re-enable exactly the POST /order replay this method exists to remove.
        HttpClient parent = new HttpClient.Builder().maxRetries(3).build();
        HttpClient orderClient = parent.withoutConnectionFailureRetry();

        // One retryable failure, then a response that must never be reached.
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"boom\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        String url = server.url("/order").toString();
        assertThrows(
            HttpStatusException.class,
            () -> orderClient.postJsonRaw(url, Collections.emptyMap(), "{}"));

        assertEquals(
            1,
            server.getRequestCount(),
            "the order client must attempt POST /order exactly once, even though the parent client "
                + "was built with maxRetries > 0");
    }

    @Test
    @DisplayName(
        "TC-HCP-006 submitOrder attempts a failing POST /order exactly once when maxRetries > 0")
    void submitOrderIsAttemptedExactlyOnceEvenWithParentMaxRetries() throws IOException {
        PolymarketClient client =
            new PolymarketClient.Builder()
                .privateKey("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80")
                .clobHost(server.url("/").toString())
                .apiCreds(new ApiKeyCreds("test-key", "c2VjcmV0", "pass123"))
                .maxRetries(3) // caller wants read resilience; must not leak into order placement
                .build();

        // One retryable failure, then a response that would prove a silent replay happened.
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"boom\"}"));
        server.enqueue(
            new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\":true,\"orderID\":\"0xabc\",\"status\":\"matched\"}"));

        OrderSubmission submission = client.submitOrder(orderPayload());

        assertEquals(
            1,
            server.getRequestCount(),
            "a caller's read-resilience maxRetries must never cause POST /order to be replayed");
        // The single attempt failed, so the disposition must not be a phantom ACCEPTED from the
        // never-sent second attempt.
        assertFalse(submission.isAccepted());
    }

    private static PostOrderPayload orderPayload() {
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
}
