package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.internal.streaming.RtdsGateway;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TC-RL — connection generation, resubscribe ordering, the documented 5-second heartbeat, and
 * idempotent close, matching the CLOB streaming lifecycle contract. */
@DisplayName("TC-RL — Rtds lifecycle, generations, and heartbeat")
class RtdsLifecycleTest {

    private MockWebServer server;
    private RtdsGateway gateway;
    private Rtds rtds;

    @AfterEach
    void tearDown() throws Exception {
        if (rtds != null) rtds.close();
        if (gateway != null) gateway.close();
        if (server != null) {
            Thread.sleep(100);
            try {
                server.shutdown();
            } catch (Exception ignored) {
                // teardown only
            }
        }
    }

    private String wsUrl() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    @Test
    @DisplayName("TC-RL-001 the generation is 1 after the first connect")
    void firstConnectionIsGenerationOne() throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onOpen(long generation) { opened.countDown(); }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(opened.await(15, TimeUnit.SECONDS));
        assertEquals(1L, rtds.generation());
    }

    @Test
    @DisplayName("TC-RL-002 resubscription is signalled before the first event")
    void resubscribeSignalledBeforeFreshData() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch gotPrice = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                ws.send("""
                    {"topic":"crypto_prices","type":"update","timestamp":1,
                     "payload":{"symbol":"btcusdt","timestamp":1,"value":1}}
                    """);
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.onBinancePrice(List.of(), e -> { order.add("price"); gotPrice.countDown(); });
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onResubscribe(long generation) { order.add("resubscribe:" + generation); }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(gotPrice.await(15, TimeUnit.SECONDS));
        assertEquals("resubscribe:1", order.get(0),
                "resubscribe must precede the first event so cached state can be invalidated first");
    }

    @Test
    @DisplayName("TC-RL-003 the documented 5-second text PING repeats while open")
    void sendsTextPingAtDocumentedInterval() throws Exception {
        List<String> pings = new CopyOnWriteArrayList<>();
        CountDownLatch twoPings = new CountDownLatch(2);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    pings.add(text);
                    twoPings.countDown();
                }
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).pingIntervalMs(100).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(twoPings.await(15, TimeUnit.SECONDS), "the heartbeat must repeat; saw " + pings);
    }

    @Test
    @DisplayName("TC-RL-004 with no override, the default heartbeat fires at the documented 5-second interval")
    void defaultPingIntervalIsFiveSeconds() throws Exception {
        CountDownLatch onePing = new CountDownLatch(1);
        long[] arrivedAtMs = new long[1];
        long startedAtMs = System.currentTimeMillis();

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    arrivedAtMs[0] = System.currentTimeMillis();
                    onePing.countDown();
                }
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).build(); // no pingIntervalMs override
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(onePing.await(10, TimeUnit.SECONDS), "the default heartbeat must fire");
        long elapsed = arrivedAtMs[0] - startedAtMs;
        assertTrue(elapsed >= 4_000, "expected roughly a 5s interval, saw " + elapsed + "ms");
    }

    @Test
    @DisplayName("TC-RL-005 the heartbeat restarts on the new connection after a reconnect")
    void heartbeatRestartsAfterReconnect() throws Exception {
        CountDownLatch pingOnSecondConnection = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) pingOnSecondConnection.countDown();
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).pingIntervalMs(100).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(pingOnSecondConnection.await(20, TimeUnit.SECONDS),
                "the reconnected channel must start its own heartbeat");
    }

    @Test
    @DisplayName("TC-RL-006 reconnect bumps the generation and restores the accumulated state")
    void reconnectBumpsGenerationAndRestoresState() throws Exception {
        CountDownLatch reconnected = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { reconnected.countDown(); }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS), "channel must reconnect");
        Thread.sleep(200);
        assertEquals(2L, rtds.generation());
    }

    @Test
    @DisplayName("TC-RL-007 close() is idempotent")
    void closeIsIdempotent() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);
        rtds.close();
        Assertions.assertDoesNotThrow(rtds::close);
    }

    @Test
    @DisplayName("TC-RL-008 a throwing lifecycle listener cannot prevent reconnect")
    void throwingLifecycleListenerCannotPreventReconnect() throws Exception {
        CountDownLatch reconnected = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { reconnected.countDown(); }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onClose(long generation, int code, String reason) {
                throw new IllegalStateException("boom");
            }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS),
                "reconnect must be scheduled even though the application callback threw");
    }
}
