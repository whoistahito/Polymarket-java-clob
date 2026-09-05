package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies the bodyless, L2-signed POST /heartbeats dead-man signal pinned by heartbeat.json. */
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

    @Test
    void shouldRemainInactiveWhenConstructed() throws Exception {
        try (Polymarket sdk = authenticatedSdk()) {
            assertFalse(sdk.isHeartbeatActive());
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowAuthenticationRequiredExceptionWhenStartingHeartbeatWithoutCredentials()
            throws Exception {
        try (Polymarket sdk = unauthenticatedSdk()) {
            assertThrows(AuthenticationRequiredException.class, sdk::startHeartbeat);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldBecomeActiveWhenHeartbeatStarts() throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertTrue(sdk.isHeartbeatActive());
        }
    }

    @Test
    void shouldKeepOneScheduleWhenHeartbeatStartsTwice() throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertDoesNotThrow(() -> sdk.startHeartbeat(TICK));
            assertTrue(sdk.isHeartbeatActive());
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "expected a heartbeat request");

            sdk.stopHeartbeat();
            assertFalse(sdk.isHeartbeatActive());
            drainInFlightTicks();
            assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS),
                    "one stop() must silence every schedule the two starts created");
        }
    }

    @Test
    void shouldSendBodylessHeartbeatWhenTicking() throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("POST", request.getMethod());
            assertEquals("/heartbeats", request.getPath());
            // The operation declares no requestBody: genuinely empty, not "null" and not "{}".
            assertEquals("", request.getBody().readUtf8());
            assertEquals(0L, request.getBodySize());
        }
    }

    @Test
    void shouldNotStartIdentifierChainWhenAcknowledged() throws Exception {
        // Even an id the unlisted /v1/heartbeats variant would chain must not leak into a tick.
        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\",\"heartbeat_id\":\"hb-first\"}"));
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertEquals("", server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());

            RecordedRequest second = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("/heartbeats", second.getPath());
            assertEquals("", second.getBody().readUtf8());
            assertTrue(sdk.isHeartbeatActive());
        }
    }

    @Test
    void shouldKeepSchedulingWhenTickFails() throws Exception {
        // 500 "Internal server error" is a documented heartbeat response (clob-openapi.yaml).
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"error\":\"Internal server error\"}"));
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertEquals("/heartbeats", server.takeRequest(5, TimeUnit.SECONDS).getPath());

            RecordedRequest afterFailure = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("/heartbeats", afterFailure.getPath());
            assertEquals("", afterFailure.getBody().readUtf8());
            assertTrue(sdk.isHeartbeatActive(), "a failed tick must leave the Heartbeat active");
        }
    }

    @Test
    void shouldCancelFutureTicksWhenStopped() throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "expected a heartbeat request");
            sdk.stopHeartbeat();
            assertFalse(sdk.isHeartbeatActive());
            drainInFlightTicks();
            assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS),
                    "no further heartbeat requests should arrive after stopHeartbeat()");
        }
    }

    @Test
    void shouldBeIdempotentWhenAlreadyStopped() throws Exception {
        try (Polymarket sdk = authenticatedSdk()) {
            assertDoesNotThrow(sdk::stopHeartbeat);
            assertDoesNotThrow(sdk::stopHeartbeat);
            assertFalse(sdk.isHeartbeatActive());
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenStartingHeartbeatWithSubMillisecondInterval()
            throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.startHeartbeat(Duration.ofNanos(1)));

            assertFalse(sdk.isHeartbeatActive(),
                    "a refused interval must not leave the dead-man switch reported as running");
            assertDoesNotThrow(() -> sdk.startHeartbeat(TICK),
                    "a refused start must not consume the one schedule");
            assertTrue(sdk.isHeartbeatActive());
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS),
                    "the accepted interval, not the refused one, is what beats");
        }
    }

    @Test
    void shouldStopHeartbeatWhenRootIsClosedTwice() throws Exception {
        enqueueAcknowledgements();
        Polymarket sdk = authenticatedSdk();
        sdk.startHeartbeat(TICK);
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "expected a heartbeat request");

        assertDoesNotThrow(sdk::close);
        assertDoesNotThrow(sdk::close);
        assertFalse(sdk.isHeartbeatActive());

        drainInFlightTicks();
        assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS),
                "no further heartbeat requests should arrive after close()");
    }

    @Test
    void shouldKeepSchedulingWhenTransportFails() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "expected the lost request");
            RecordedRequest afterLoss = nextDeliveredRequest();
            assertEquals("/heartbeats", afterLoss.getPath());
            assertEquals("", afterLoss.getBody().readUtf8());
            assertTrue(sdk.isHeartbeatActive(), "a lost tick must leave the Heartbeat active");
        }
    }

    @Test
    void shouldResumeBeatingWhenRestarted() throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "expected a heartbeat request");
            sdk.stopHeartbeat();
            drainInFlightTicks();

            sdk.startHeartbeat(TICK);
            assertTrue(sdk.isHeartbeatActive());
            RecordedRequest resumed = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("/heartbeats", resumed.getPath());
            assertEquals("", resumed.getBody().readUtf8());
        }
    }

    @Test
    void shouldSignEmptyBodyWhenTicking() throws Exception {
        enqueueAcknowledgements();
        try (Polymarket sdk = authenticatedSdk()) {
            sdk.startHeartbeat(TICK);
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);

            // The five securitySchemes the sendHeartbeat operation requires.
            assertEquals(CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
            assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
            assertEquals(CREDENTIALS.passphrase(), request.getHeader("POLY_PASSPHRASE"));
            String timestamp = request.getHeader("POLY_TIMESTAMP");
            assertNotNull(timestamp);
            assertEquals(hmac(timestamp + "POST" + "/heartbeats"),
                    request.getHeader("POLY_SIGNATURE"));
        }
    }

    private Polymarket authenticatedSdk() {
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(configPointingAtServer(), authority);
    }

    private Polymarket unauthenticatedSdk() {
        return Polymarket.with(configPointingAtServer());
    }

    private PolymarketConfig configPointingAtServer() {
        URI host = server.url("/").newBuilder().host("127.0.0.1").build().uri();
        return PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
    }

    /** The documented acknowledgement: HeartbeatResponse carries only {@code status}. */
    private void enqueueAcknowledgements() {
        for (int i = 0; i < 40; i++) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        }
    }

    private void drainInFlightTicks() throws Exception {
        while (server.takeRequest(TICK.toMillis() * 2, TimeUnit.MILLISECONDS) != null) {
        }
    }

    /** Skips the bookkeeping entry MockWebServer records for a dropped connection. */
    private RecordedRequest nextDeliveredRequest() throws Exception {
        for (int i = 0; i < 10; i++) {
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            if (request != null && request.getPath() != null) {
                return request;
            }
        }
        throw new AssertionError("no heartbeat request was delivered after the transport failure");
    }

    /** HMAC-SHA256 recomputed here so the SDK's own signer cannot vouch for itself. */
    private static String hmac(String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                Base64.getUrlDecoder().decode(CREDENTIALS.secret()), "HmacSHA256"));
        return Base64.getUrlEncoder()
                .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
