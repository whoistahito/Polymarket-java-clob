package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Issue #24: explicit heartbeat lifecycle owned by the {@link Polymarket} root. */
@DisplayName("Heartbeat: explicit lifecycle (issue #24)")
class HeartbeatTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Duration TICK = Duration.ofMillis(30);

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    private Polymarket authenticatedSdk() {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(config, authority);
    }

    private Polymarket unauthenticatedSdk() {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config);
    }

    @Test
    @DisplayName("TC-HB-001: heartbeat does not start on construction")
    void doesNotStartOnConstruction() throws Exception {
        try (Polymarket sdk = authenticatedSdk()) {
            assertFalse(sdk.isHeartbeatActive());
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-HB-002: startHeartbeat() without L2 credentials throws before any request")
    void startWithoutCredentialsThrows() throws Exception {
        try (Polymarket sdk = unauthenticatedSdk()) {
            assertThrows(AuthenticationRequiredException.class, sdk::startHeartbeat);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-HB-003: isHeartbeatActive() is true once started")
    void activeAfterStart() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-1\"}"));
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertTrue(sdk.isHeartbeatActive());
        }
    }

    @Test
    @DisplayName("TC-HB-004: double startHeartbeat() throws IllegalStateException")
    void doubleStartThrows() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-1\"}"));
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertThrows(IllegalStateException.class, () -> sdk.startHeartbeat(TICK));
        }
    }

    @Test
    @DisplayName("TC-HB-005: the first tick posts the documented empty heartbeat_id")
    void firstTickOmitsHeartbeatId() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-1\"}"));
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("POST", request.getMethod());
            assertEquals("/v1/heartbeats", request.getPath());
            assertEquals(CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
            assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
            assertEquals("{\"heartbeat_id\":\"\"}", request.getBody().readUtf8());
        }
    }

    @Test
    @DisplayName("TC-HB-006: the second tick chains the id the first response returned")
    void secondTickChainsReturnedId() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-first\"}"));
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-second\"}"));
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("{\"heartbeat_id\":\"\"}", first.getBody().readUtf8());
            RecordedRequest second = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("{\"heartbeat_id\":\"hb-first\"}", second.getBody().readUtf8());
        }
    }

    @Test
    @DisplayName("TC-HB-007: a failed tick (HTTP 500) does not cancel future scheduling")
    void failedTickDoesNotStopScheduling() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-recovered\"}"));
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("{\"heartbeat_id\":\"\"}", first.getBody().readUtf8());
            RecordedRequest second = server.takeRequest(5, TimeUnit.SECONDS);
            // The failed tick's id (empty) was never updated, so the retry resends it unchanged.
            assertEquals("{\"heartbeat_id\":\"\"}", second.getBody().readUtf8());
            assertTrue(sdk.isHeartbeatActive(), "manager must still be active after a failed tick");
        }
    }

    @Test
    @DisplayName("TC-HB-008: stop() cancels future ticks")
    void stopCancelsFutureTicks() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-1\"}"));
        for (int i = 0; i < 20; i++) {
            server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-x\"}"));
        }
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertNotNullRequest(server.takeRequest(5, TimeUnit.SECONDS));
            sdk.stopHeartbeat();
            assertFalse(sdk.isHeartbeatActive());
            // Drain anything already in flight, then confirm silence.
            while (server.takeRequest(TICK.toMillis() * 2, TimeUnit.MILLISECONDS) != null) {
                // in-flight tick landed right as stop() fired; keep draining briefly
            }
            assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS),
                    "no further heartbeat requests should arrive after stop()");
        }
    }

    @Test
    @DisplayName("TC-HB-009: stop() is idempotent when not active")
    void stopIdempotentWhenNotActive() throws Exception {
        try (Polymarket sdk = authenticatedSdk()) {
            assertDoesNotThrow(sdk::stopHeartbeat);
            assertDoesNotThrow(sdk::stopHeartbeat);
        }
    }

    @Test
    @DisplayName("TC-HB-010: closing the root twice stops the heartbeat and throws no error")
    void closeIsIdempotentAndStopsHeartbeat() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-1\"}"));
        for (int i = 0; i < 20; i++) {
            server.enqueue(new MockResponse().setBody("{\"heartbeat_id\":\"hb-x\"}"));
        }
        Polymarket sdk = authenticatedSdk();
        sdk.startHeartbeat(TICK);
        assertNotNullRequest(server.takeRequest(5, TimeUnit.SECONDS));

        assertDoesNotThrow(sdk::close);
        assertDoesNotThrow(sdk::close);

        while (server.takeRequest(TICK.toMillis() * 2, TimeUnit.MILLISECONDS) != null) {
            // drain any tick that was already in flight when close() fired
        }
        assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS),
                "no further heartbeat requests should arrive after close()");
    }

    private static void assertNotNullRequest(RecordedRequest request) {
        assertTrue(request != null, "expected a heartbeat request");
    }
}
