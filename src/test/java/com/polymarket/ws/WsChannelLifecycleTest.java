package com.polymarket.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.model.WsMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 027 — channel-identified lifecycle callbacks and the documented text heartbeat.
 *
 * <p>A consumer that caches order books has to know WHICH channel dropped and whether the data it
 * holds predates the current connection. That needs a channel tag and a connection generation on
 * every lifecycle callback, reconnect that survives a throwing application callback, and the
 * documented {@code PING} every 10 seconds rather than an OkHttp protocol ping.
 */
@DisplayName("TC-WSL — channel lifecycle and heartbeats (Ticket 027)")
class WsChannelLifecycleTest {

    private MockWebServer server;
    private WsClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) client.close();
        if (server != null) {
            // Give the closing handshake a moment: MockWebServer's shutdown blocks on a socket that
            // is still open, and a slow teardown here would be reported as a test failure.
            Thread.sleep(100);
            try {
                server.shutdown();
            } catch (Exception ignored) {
                // teardown only — a lingering mock socket must not mask the assertions above
            }
        }
    }

    private String wsBase() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    private record Lifecycle(ChannelType channel, long generation, String event) {}

    // ------------------------------------------------------------------ //
    // Reconnect survives throwing application callbacks                   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSL-001 a throwing onClose cannot prevent reconnect")
    void throwingOnCloseCannotPreventReconnect() throws Exception {
        CountDownLatch reconnected = new CountDownLatch(1);
        server = new MockWebServer();
        // First connection: the server drops it as soon as the subscription frame arrives.
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { reconnected.countDown(); }
        }));
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .reconnectDelayMs(50)
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage message) {}
                @Override public void onError(Exception error) { throw new IllegalStateException("boom"); }
                @Override public void onClose(int code, String reason) { throw new IllegalStateException("boom"); }
            })
            .build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS),
            "reconnect must be scheduled even though the application callback threw");
        assertNotNull(client.getMarketWebSocket());
    }

    @Test
    @DisplayName("TC-WSL-002 a throwing onError cannot prevent reconnect after a failure")
    void throwingOnErrorCannotPreventReconnect() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        server = new MockWebServer();
        // The first attempt fails at the socket level, which routes through onFailure -> onError.
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { connected.countDown(); }
        }));
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .reconnectDelayMs(50)
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage message) {}
                @Override public void onError(Exception error) { throw new IllegalStateException("boom"); }
                @Override public void onClose(int code, String reason) {}
            })
            .build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(connected.await(15, TimeUnit.SECONDS),
            "a failure followed by a throwing onError must still reconnect");
    }

    // ------------------------------------------------------------------ //
    // Channel-identified lifecycle with a connection generation           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSL-003 lifecycle callbacks identify the channel and its generation")
    void lifecycleCallbacksCarryChannelAndGeneration() throws Exception {
        List<Lifecycle> events = new CopyOnWriteArrayList<>();
        CountDownLatch opened = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage message) {}
                @Override public void onError(Exception error) {}
                @Override public void onClose(int code, String reason) {}
                @Override public void onOpen(ChannelType channel, long generation) {
                    events.add(new Lifecycle(channel, generation, "open"));
                    opened.countDown();
                }
            })
            .build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(opened.await(15, TimeUnit.SECONDS));
        assertEquals(ChannelType.MARKET, events.get(0).channel());
        assertEquals(1L, events.get(0).generation(), "the first connection is generation 1");
        assertEquals(1L, client.getConnectionGeneration(ChannelType.MARKET));
    }

    @Test
    @DisplayName("TC-WSL-004 the generation increments on each reconnect of that channel")
    void generationIncrementsOnReconnect() throws Exception {
        CountDownLatch twoOpens = new CountDownLatch(2);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .reconnectDelayMs(50)
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage message) {}
                @Override public void onError(Exception error) {}
                @Override public void onClose(int code, String reason) {}
                @Override public void onOpen(ChannelType channel, long generation) { twoOpens.countDown(); }
            })
            .build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(twoOpens.await(20, TimeUnit.SECONDS));
        assertEquals(2L, client.getConnectionGeneration(ChannelType.MARKET));
    }

    @Test
    @DisplayName("TC-WSL-005 market and user generations move independently")
    void generationsAreIndependentPerChannel() throws Exception {
        CountDownLatch marketOpen = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .apiKeyCreds(new ApiKeyCreds("key", "c2VjcmV0", "pass"))
            .walletAddress("0x1")
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage message) {}
                @Override public void onError(Exception error) {}
                @Override public void onClose(int code, String reason) {}
                @Override public void onOpen(ChannelType channel, long generation) {
                    if (channel == ChannelType.MARKET) marketOpen.countDown();
                }
            })
            .build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(marketOpen.await(15, TimeUnit.SECONDS));
        assertEquals(1L, client.getConnectionGeneration(ChannelType.MARKET));
        assertEquals(0L, client.getConnectionGeneration(ChannelType.USER),
            "an unopened user channel must still be at generation 0");
    }

    @Test
    @DisplayName("TC-WSL-006 resubscription is signalled before fresh data arrives")
    void resubscribeSignalledBeforeFreshData() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch gotBook = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if (text.startsWith("{")) {
                    ws.send("""
                        {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
                         "bids":[],"asks":[],"hash":"h"}
                        """);
                }
            }
        }));
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage message) {
                    order.add("message");
                    gotBook.countDown();
                }
                @Override public void onError(Exception error) {}
                @Override public void onClose(int code, String reason) {}
                @Override public void onResubscribe(ChannelType channel, long generation) {
                    order.add("resubscribe:" + channel + ":" + generation);
                }
            })
            .build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(gotBook.await(15, TimeUnit.SECONDS));
        assertEquals("resubscribe:MARKET:1", order.get(0),
            "the resubscribe signal must precede the first frame so books can be invalidated");
    }

    // ------------------------------------------------------------------ //
    // Documented text PING heartbeat                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSL-007 the client sends the documented text PING while a channel is open")
    void sendsTextPingWhileOpen() throws Exception {
        List<String> pings = new CopyOnWriteArrayList<>();
        CountDownLatch twoPings = new CountDownLatch(2);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    pings.add(text);
                    twoPings.countDown();
                    ws.send("PONG");
                }
            }
        }));
        server.start();

        client = WsClient.builder().wsBase(wsBase()).pingIntervalMs(100).build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(twoPings.await(15, TimeUnit.SECONDS),
            "the heartbeat must repeat, not fire once; saw " + pings);
        assertEquals("PING", pings.get(0), "the documented heartbeat is the literal text PING");
    }

    @Test
    @DisplayName("TC-WSL-008 the heartbeat stops once the client is closed")
    void heartbeatStopsOnClose() throws Exception {
        AtomicInteger pings = new AtomicInteger();
        CountDownLatch firstPing = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    pings.incrementAndGet();
                    firstPing.countDown();
                }
            }
        }));
        server.start();

        client = WsClient.builder().wsBase(wsBase()).pingIntervalMs(100).build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(firstPing.await(15, TimeUnit.SECONDS));
        client.close();
        int afterClose = pings.get();
        Thread.sleep(600);

        assertTrue(pings.get() <= afterClose + 1,
            "the heartbeat must be cancelled on close, saw " + pings.get() + " after " + afterClose);
    }

    @Test
    @DisplayName("TC-WSL-009 the heartbeat restarts on the new connection after a reconnect")
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

        client = WsClient.builder()
            .wsBase(wsBase()).pingIntervalMs(100).reconnectDelayMs(50).build();
        client.subscribeMarket(List.of("tokA"));

        assertTrue(pingOnSecondConnection.await(20, TimeUnit.SECONDS),
            "the reconnected channel must start its own heartbeat");
    }

    // ------------------------------------------------------------------ //
    // Retry limits stay meaningful                                        //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSL-010 repeated handshake-then-close loops respect maxReconnectAttempts")
    void handshakeThenCloseRespectsRetryLimit() throws Exception {
        AtomicInteger handshakes = new AtomicInteger();

        server = new MockWebServer();
        for (int i = 0; i < 20; i++) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override public void onOpen(WebSocket ws, Response response) {
                    handshakes.incrementAndGet();
                    ws.close(1011, "immediate close"); // accept, then drop straight away
                }
            }));
        }
        server.start();

        client = WsClient.builder()
            .wsBase(wsBase())
            .reconnectDelayMs(20)
            .maxReconnectDelayMs(50)
            .maxReconnectAttempts(3)
            .build();
        client.subscribeMarket(List.of("tokA"));

        Thread.sleep(3_000);

        // 1 initial connection + at most 3 reconnect attempts. Without a stability requirement the
        // attempt counter reset on every successful handshake and this looped forever.
        assertTrue(handshakes.get() <= 4,
            "expected at most 4 handshakes with maxReconnectAttempts=3, saw " + handshakes.get());
        assertTrue(handshakes.get() >= 2, "reconnect must actually be attempted");
    }
}
