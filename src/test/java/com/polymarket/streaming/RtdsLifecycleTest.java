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
import org.junit.jupiter.api.Test;

/** Covers RTDS lifecycle behavior and its documented five-second heartbeat contract. */
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
            }
        }
    }

    private String wsUrl() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    @Test
    void shouldStartAtGenerationOneWhenRtdsConnects() throws Exception {
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
    void shouldSignalResubscribeBeforeFreshDataWhenRtdsConnects() throws Exception {
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
    void shouldSendTextPingsWhenRtdsChannelIsOpen() throws Exception {
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
    void shouldUseFiveSecondPingIntervalWhenNoOverrideIsConfigured() throws Exception {
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

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(onePing.await(10, TimeUnit.SECONDS), "the default heartbeat must fire");
        long elapsed = arrivedAtMs[0] - startedAtMs;
        assertTrue(elapsed >= 4_000, "expected roughly a 5s interval, saw " + elapsed + "ms");
    }

    @Test
    void shouldRestartHeartbeatWhenRtdsReconnects() throws Exception {
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
    void shouldRestoreStateAndBumpGenerationWhenRtdsReconnects() throws Exception {
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
    void shouldCloseIdempotentlyWhenCloseIsCalledTwice() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);
        rtds.close();
        Assertions.assertDoesNotThrow(rtds::close);
    }

    @Test
    void shouldReleaseOwnedTransportResourcesWhenRtdsCloses() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
        }));
        server.start();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        rtds.close();

        assertTrue(gateway.isClosed(),
                "close() must release the scheduler, dispatcher and connection pool it owns");
        assertEquals(0, gateway.connectionPoolSize());
    }

    @Test
    void shouldDeliverNoCallbacksWhenRtdsIsClosed() {
        // A frame may be in flight during close, so the closed-state guard belongs at dispatch.
        CapturingTransport transport = new CapturingTransport();
        Rtds capability = new Rtds(transport);
        List<BinancePriceEvent> seen = new CopyOnWriteArrayList<>();
        capability.onBinancePrice(List.of(), seen::add);
        capability.subscribeBinancePrices(List.of("btcusdt"));

        capability.close();
        transport.sink.onBinancePrice(
                new BinancePriceEvent("btcusdt", 1L, 1L, java.math.BigDecimal.ONE));

        assertEquals(List.of(), seen, "a closed capability must deliver nothing");
    }

    private static final class CapturingTransport implements RtdsTransport {
        private RtdsEventSink sink;

        @Override
        public RtdsConnection connect(RtdsSubscriptions subscriptions, RtdsEventSink sink) {
            this.sink = sink;
            return new RtdsConnection() {
                @Override
                public void subscription(RtdsSubscriptions current) {
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void close() {
        }
    }

    @Test
    void shouldContinueReconnectWhenLifecycleListenerThrows() throws Exception {
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
