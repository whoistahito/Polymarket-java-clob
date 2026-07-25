package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
