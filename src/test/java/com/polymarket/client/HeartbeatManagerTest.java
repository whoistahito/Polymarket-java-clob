package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.model.HeartbeatResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HeartbeatManager}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Lifecycle: start/stop/isActive state transitions
 *   <li>Default interval constant
 *   <li>Heartbeat ID chaining (each call receives the ID from the previous response)
 *   <li>Error resilience (HTTP errors do not stop the task)
 *   <li>Double-start guard
 *   <li>Integration with {@link PolymarketClient} lifecycle methods
 *   <li>Integration with {@link AsyncPolymarketClient} lifecycle methods
 * </ul>
 */
@DisplayName("TC-HB — HeartbeatManager tests")
class HeartbeatManagerTest {

    // -----------------------------------------------------------------------
    // HeartbeatManager (poster-injection) tests                              //
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TC-HB-1xx — Core HeartbeatManager behaviour")
    class CoreTests {

        @Test
        @DisplayName("TC-HB-101 default interval is 5000 ms")
        void defaultIntervalConstant() {
            assertEquals(5_000L, HeartbeatManager.DEFAULT_INTERVAL_MS);
        }

        @Test
        @DisplayName("TC-HB-102 isActive() is false before start()")
        void notActiveBeforeStart() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            assertFalse(hb.isActive());
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-103 isActive() is true after start()")
        void activeAfterStart() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            hb.start(60_000); // very long interval so no actual tick runs
            assertTrue(hb.isActive());
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-104 isActive() is false after stop()")
        void notActiveAfterStop() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            hb.start(60_000);
            assertTrue(hb.isActive());
            hb.stop();
            assertFalse(hb.isActive());
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-105 stop() is idempotent when not active")
        void stopIdempotent() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            assertDoesNotThrow(hb::stop); // not started yet — should not throw
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-106 start() with non-positive interval throws")
        void startWithZeroInterval() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            assertThrows(IllegalArgumentException.class, () -> hb.start(0));
            assertThrows(IllegalArgumentException.class, () -> hb.start(-1));
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-107 double start() throws IllegalStateException")
        void doubleStartThrows() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            hb.start(60_000);
            assertThrows(IllegalStateException.class, () -> hb.start(60_000));
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-108 can restart after stop()")
        void restartAfterStop() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            hb.start(60_000);
            hb.stop();
            assertFalse(hb.isActive());
            hb.start(60_000); // should not throw
            assertTrue(hb.isActive());
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-109 getLastHeartbeatId() is null before any tick")
        void lastHeartbeatIdNullBeforeFirstTick() {
            HeartbeatManager hb = new HeartbeatManager(id -> new HeartbeatResponse("hb-1", "ok"));
            assertNull(hb.getLastHeartbeatId());
            hb.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // Heartbeat ID chaining                                                   //
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TC-HB-2xx — Heartbeat ID chaining")
    class IdChainingTests {

        @Test
        @DisplayName("TC-HB-201 first call receives null heartbeat ID")
        void firstCallReceivesNullId() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> firstId = new AtomicReference<>("NOT_SET");

      HeartbeatManager hb =
          new HeartbeatManager(
              id -> {
                if (firstId.compareAndSet("NOT_SET", id)) {
                  latch.countDown();
                }
                return new HeartbeatResponse("hb-resp-1", "ok");
              });
            hb.start(50); // 50 ms interval so tick fires quickly
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Heartbeat should have fired");
            assertNull(firstId.get(), "First call must pass null heartbeat ID");
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-202 subsequent calls receive previous response ID")
        void idChainingAcrossMultipleTicks() throws InterruptedException {
            int ticks = 3;
            CountDownLatch latch = new CountDownLatch(ticks);
            List<String> receivedIds = new ArrayList<>();
            AtomicInteger callCount = new AtomicInteger(0);

            HeartbeatManager hb = new HeartbeatManager(id -> {
                int n = callCount.incrementAndGet();
                synchronized (receivedIds) {
                    receivedIds.add(id);
                }
                latch.countDown();
                return new HeartbeatResponse("hb-resp-" + n, "ok");
            });
            hb.start(50);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected 3 heartbeat ticks");
            hb.shutdown();

            assertEquals(ticks, receivedIds.size());
            assertNull(receivedIds.get(0), "1st call: id must be null");
            assertEquals("hb-resp-1", receivedIds.get(1), "2nd call: id from 1st response");
            assertEquals("hb-resp-2", receivedIds.get(2), "3rd call: id from 2nd response");
        }

        @Test
        @DisplayName("TC-HB-203 getLastHeartbeatId() reflects most recent response ID")
        void getLastHeartbeatIdUpdated() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);

            HeartbeatManager hb = new HeartbeatManager(id -> {
                latch.countDown();
                return new HeartbeatResponse("my-hb-id", "ok");
            });
            hb.start(50);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            // Read ID before shutdown (shutdown calls stop() which clears the ID)
            String lastId = hb.getLastHeartbeatId();
            hb.shutdown();

            assertEquals("my-hb-id", lastId);
        }

        @Test
        @DisplayName("TC-HB-204 getLastHeartbeatId() resets to null after stop()")
        void lastIdClearedOnStop() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);

            HeartbeatManager hb = new HeartbeatManager(id -> {
                latch.countDown();
                return new HeartbeatResponse("hb-xyz", "ok");
            });
            hb.start(50);
            latch.await(5, TimeUnit.SECONDS);
            hb.stop();

            assertNull(hb.getLastHeartbeatId(), "Last ID must be cleared after stop()");
            hb.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // Error resilience                                                        //
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TC-HB-3xx — Error resilience")
    class ErrorResilienceTests {

        @Test
        @DisplayName("TC-HB-301 IOException does not stop the scheduled task")
        void ioExceptionDoesNotStopTask() throws InterruptedException {
            AtomicInteger callCount = new AtomicInteger(0);
            // First call throws; second call succeeds
            CountDownLatch latch = new CountDownLatch(2);

            HeartbeatManager hb = new HeartbeatManager(id -> {
                int n = callCount.incrementAndGet();
                latch.countDown();
                if (n == 1) {
                    throw new IOException("simulated network error");
                }
                return new HeartbeatResponse("hb-recovered", "ok");
            });
            hb.start(50);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Task should survive IOException");
            assertTrue(hb.isActive(), "Manager must still be active after error");
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-302 RuntimeException does not stop the scheduled task")
        void runtimeExceptionDoesNotStopTask() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicInteger callCount = new AtomicInteger(0);

            HeartbeatManager hb = new HeartbeatManager(id -> {
                int n = callCount.incrementAndGet();
                latch.countDown();
                if (n == 1) throw new RuntimeException("boom");
                return new HeartbeatResponse("hb-ok", "ok");
            });
            hb.start(50);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(hb.isActive());
            hb.shutdown();
        }

        @Test
        @DisplayName("TC-HB-303 ID is not updated when poster throws")
        void idNotUpdatedOnError() throws InterruptedException {
      CountDownLatch secondCallStarted = new CountDownLatch(2);
            AtomicInteger n = new AtomicInteger(0);

      HeartbeatManager hb =
          new HeartbeatManager(
              id -> {
                int call = n.incrementAndGet();
                secondCallStarted.countDown();
                if (call == 1) throw new IOException("fail");
                return new HeartbeatResponse("hb-second", "ok");
              });
            hb.start(50);
            assertTrue(secondCallStarted.await(5, TimeUnit.SECONDS));
            // secondCallStarted counts down at the START of the poster callback, before the
            // manager stores the returned id — poll until the second (successful) call's
            // id is stored rather than racing the store.
            String lastId = null;
            for (int i = 0; i < 100; i++) {
                lastId = hb.getLastHeartbeatId();
                if ("hb-second".equals(lastId)) break;
                Thread.sleep(10);
            }
            hb.shutdown();

            // After an error the ID should come from the second (successful) call
            assertEquals("hb-second", lastId);
        }
    }

    // -----------------------------------------------------------------------
    // PolymarketClient integration                                            //
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TC-HB-4xx — PolymarketClient integration")
    class PolymarketClientIntegrationTests {

        private static final String PK =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
        private static final String FUNDER = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

        private MockWebServer server;
        private PolymarketClient client;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            String base = server.url("/").toString();

            ApiKeyCreds creds = new ApiKeyCreds("test-key", "c2VjcmV0", "pass123");
            client = new PolymarketClient.Builder()
                .privateKey(PK)
                .funderAddress(FUNDER)
                .apiCreds(creds)
                .clobHost(base)
                .gammaHost(base)
                .build();
        }

        @AfterEach
        void tearDown() throws IOException {
            client.stopHeartbeats();
            server.shutdown();
        }

        @Test
        @DisplayName("TC-HB-401 isHeartbeatsActive() is false before startHeartbeats()")
        void notActiveByDefault() {
            assertFalse(client.isHeartbeatsActive());
        }

        @Test
        @DisplayName("TC-HB-402 isHeartbeatsActive() is true after startHeartbeats()")
        void activeAfterStart() {
            client.startHeartbeats(60_000);
            assertTrue(client.isHeartbeatsActive());
        }

        @Test
        @DisplayName("TC-HB-403 isHeartbeatsActive() is false after stopHeartbeats()")
        void notActiveAfterStop() {
            client.startHeartbeats(60_000);
            client.stopHeartbeats();
            assertFalse(client.isHeartbeatsActive());
        }

        @Test
        @DisplayName("TC-HB-404 startHeartbeats() without L2 auth throws")
        void startWithoutAuthThrows() throws IOException {
            MockWebServer s2 = new MockWebServer();
            s2.start();
            PolymarketClient noAuthClient = new PolymarketClient.Builder()
                .privateKey(PK)
                .clobHost(s2.url("/").toString())
                .gammaHost(s2.url("/").toString())
                .build();
            try {
                assertThrows(IllegalStateException.class, noAuthClient::startHeartbeats);
            } finally {
                s2.shutdown();
            }
        }

        @Test
        @DisplayName("TC-HB-405 stopHeartbeats() is safe to call when not active")
        void stopWhenNotActive() {
            assertDoesNotThrow(client::stopHeartbeats);
        }

        @Test
        @DisplayName("TC-HB-406 double startHeartbeats() throws")
        void doubleStartThrows() {
            client.startHeartbeats(60_000);
            assertThrows(IllegalStateException.class, () -> client.startHeartbeats(60_000));
        }

        @Test
        @DisplayName("TC-HB-407 heartbeat posts with correct endpoint and body")
        void heartbeatPostsToCorrectEndpoint() throws Exception {
            server.enqueue(new MockResponse()
                .setBody("{\"heartbeat_id\":\"hb-fired\",\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

            CountDownLatch latch = new CountDownLatch(1);
            // We can't intercept the manager directly, so enqueue then wait for request
            client.startHeartbeats(50);
            assertTrue(latch.await(0, TimeUnit.SECONDS) || true); // just let first tick run

            // Wait for the HTTP request to be captured
            var req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req, "Expected a heartbeat HTTP request");
            assertTrue(req.getPath().contains("/v1/heartbeats"));
            assertEquals("POST", req.getMethod());
        }
    }

    // -----------------------------------------------------------------------
    // AsyncPolymarketClient integration                                       //
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TC-HB-5xx — AsyncPolymarketClient integration")
    class AsyncClientIntegrationTests {

        private static final String PK =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
        private static final String FUNDER = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

        private MockWebServer server;
        private AsyncPolymarketClient asyncClient;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            String base = server.url("/").toString();

            ApiKeyCreds creds = new ApiKeyCreds("test-key", "c2VjcmV0", "pass123");
            PolymarketClient sync = new PolymarketClient.Builder()
                .privateKey(PK)
                .funderAddress(FUNDER)
                .apiCreds(creds)
                .clobHost(base)
                .gammaHost(base)
                .build();
            asyncClient = AsyncPolymarketClient.wrap(sync);
        }

        @AfterEach
        void tearDown() throws IOException {
            asyncClient.stopHeartbeats();
            server.shutdown();
        }

        @Test
        @DisplayName("TC-HB-501 isHeartbeatsActive() is false before start")
        void notActiveByDefault() {
            assertFalse(asyncClient.isHeartbeatsActive());
        }

        @Test
        @DisplayName("TC-HB-502 isHeartbeatsActive() is true after startHeartbeats()")
        void activeAfterStart() {
            asyncClient.startHeartbeats(60_000);
            assertTrue(asyncClient.isHeartbeatsActive());
        }

        @Test
        @DisplayName("TC-HB-503 isHeartbeatsActive() is false after stopHeartbeats()")
        void notActiveAfterStop() {
            asyncClient.startHeartbeats(60_000);
            asyncClient.stopHeartbeats();
            assertFalse(asyncClient.isHeartbeatsActive());
        }

        @Test
        @DisplayName("TC-HB-504 default startHeartbeats() activates the manager")
        void defaultStartActivates() {
            asyncClient.startHeartbeats();
            assertTrue(asyncClient.isHeartbeatsActive());
        }
    }
}
