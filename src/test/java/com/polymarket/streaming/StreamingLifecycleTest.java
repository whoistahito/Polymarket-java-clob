package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.internal.streaming.StreamingGateway;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StreamingLifecycleTest {

    private MockWebServer server;
    private StreamingGateway gateway;
    private Streaming streaming;

    @AfterEach
    void tearDown() throws Exception {
        if (streaming != null) streaming.close();
        if (gateway != null) gateway.close();
        if (server != null) {
            Thread.sleep(100);
            try {
                server.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    private String wsBase() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    @Test
    void shouldStartMarketAtGenerationOneWhenMarketChannelConnects() throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.addLifecycleListener(new StreamLifecycleListener() {
            @Override public void onOpen(StreamChannel channel, long generation) { opened.countDown(); }
        });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(opened.await(15, TimeUnit.SECONDS));
        assertEquals(1L, streaming.marketGeneration());
        assertEquals(0L, streaming.userGeneration(), "an unopened user channel stays at generation 0");
    }

    @Test
    void shouldSignalResubscribeBeforeFreshDataWhenMarketChannelConnects() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch gotBook = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                ws.send("""
                    {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
                     "bids":[],"asks":[],"hash":"h"}
                    """);
            }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.onBookUpdate(List.of("tokA"), b -> { order.add("book"); gotBook.countDown(); });
        streaming.addLifecycleListener(new StreamLifecycleListener() {
            @Override public void onResubscribe(StreamChannel channel, long generation) {
                order.add("resubscribe:" + channel + ":" + generation);
            }
        });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(gotBook.await(15, TimeUnit.SECONDS));
        assertEquals("resubscribe:MARKET:1", order.get(0),
                "resubscribe must precede the first event so cached state can be invalidated first");
    }

    @Test
    void shouldKeepGenerationsIndependentWhenOnlyMarketChannelConnects() throws Exception {
        CountDownLatch marketOpen = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.addLifecycleListener(new StreamLifecycleListener() {
            @Override public void onOpen(StreamChannel channel, long generation) {
                if (channel == StreamChannel.MARKET) marketOpen.countDown();
            }
        });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(marketOpen.await(15, TimeUnit.SECONDS));
        assertEquals(1L, streaming.marketGeneration());
        assertEquals(0L, streaming.userGeneration());
    }

    @Test
    void shouldBumpOnlyUserGenerationWhenUserChannelReconnects() throws Exception {
        CountDownLatch userReconnected = new CountDownLatch(1);
        server = new MockWebServer();
        java.util.concurrent.atomic.AtomicInteger userAttempt = new java.util.concurrent.atomic.AtomicInteger();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith("/ws/user")) {
                    if (userAttempt.incrementAndGet() == 1) {
                        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
                        });
                    }
                    return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                        @Override public void onOpen(WebSocket ws, Response response) { userReconnected.countDown(); }
                    });
                }
                return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {});
            }
        });
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).reconnectDelayMs(50).build();
        streaming = new Streaming(gateway,
                SigningAuthority.apiCredentials(new ApiCredentials("key", "secret", "pass"), "0x" + "a".repeat(40)));
        streaming.subscribeMarket(List.of("tokA"));
        streaming.subscribeUser(List.of("0xm1"));

        assertTrue(userReconnected.await(20, TimeUnit.SECONDS), "user channel must reconnect");
        Thread.sleep(200);
        assertEquals(1L, streaming.marketGeneration(), "market must not have reconnected");
        assertEquals(2L, streaming.userGeneration(), "user reconnected once: generation 2");
    }

    @Test
    void shouldThrowAuthenticationRequiredExceptionWhenUserSubscriptionLacksCredentials() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        Assertions.assertThrows(AuthenticationRequiredException.class,
                () -> streaming.subscribeUser(List.of("0xm1")));
        assertEquals(List.of(), streaming.subscribedMarkets(),
                "a rejected subscribe must not have mutated the authoritative set");
    }

    @Test
    void shouldSendTextPingsWhenStreamingChannelIsOpen() throws Exception {
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

        gateway = StreamingGateway.builder().wsBase(wsBase()).pingIntervalMs(100).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(twoPings.await(15, TimeUnit.SECONDS), "the heartbeat must repeat; saw " + pings);
    }

    @Test
    void shouldRestartHeartbeatWhenStreamingChannelReconnects() throws Exception {
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

        gateway = StreamingGateway.builder().wsBase(wsBase()).pingIntervalMs(100).reconnectDelayMs(50).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(pingOnSecondConnection.await(20, TimeUnit.SECONDS),
                "the reconnected channel must start its own heartbeat");
    }

    @Test
    void shouldCloseIdempotentlyWhenCloseIsCalledTwice() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.close();
        Assertions.assertDoesNotThrow(streaming::close);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenOperationsFollowClose() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.apiCredentials(new ApiCredentials("k", "cw==", "p"), "0x" + "a".repeat(40)));
        streaming.subscribeMarket(List.of("tokA"));
        for (int i = 0; i < 100 && server.getRequestCount() < 1; i++) Thread.sleep(50);
        streaming.close();
        int handshakes = server.getRequestCount();

        Assertions.assertThrows(IllegalStateException.class, () -> streaming.subscribeMarket(List.of("tokB")));
        Assertions.assertThrows(IllegalStateException.class, () -> streaming.subscribeUser(List.of("0xm")));
        Assertions.assertThrows(IllegalStateException.class, streaming::enableCustomMarketEvents);

        Thread.sleep(500);
        assertEquals(handshakes, server.getRequestCount(), "a closed capability must open no further socket");
        assertTrue(streaming.isClosed());
    }

    @Test
    void shouldStopCallbacksAndHeartbeatWhenStreamingCloses() throws Exception {
        List<String> pings = new CopyOnWriteArrayList<>();
        List<BookEvent> books = new CopyOnWriteArrayList<>();
        CountDownLatch twoPings = new CountDownLatch(2);
        String book = """
            {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
             "bids":[],"asks":[],"hash":"h"}
            """;

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    pings.add(text);
                    twoPings.countDown();
                }
            }

            @Override public void onClosing(WebSocket ws, int code, String reason) {
                ws.send(book); // still in flight when the client asked to close
                ws.close(code, reason);
            }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).pingIntervalMs(100).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.onBookUpdate(List.of("tokA"), books::add);
        streaming.subscribeMarket(List.of("tokA"));
        assertTrue(twoPings.await(15, TimeUnit.SECONDS), "the heartbeat must be running first");

        streaming.close();
        int pingsAtClose = pings.size();
        Thread.sleep(800);

        assertEquals(pingsAtClose, pings.size(), "no heartbeat may outlive close: " + pings);
        assertEquals(List.of(), books, "no callback may run after close");
    }

    @Test
    void shouldReopenEmptyChannelWhenSubscriptionFollowsDisconnect() throws Exception {
        // An empty channel need not reconnect, but a later subscription must revive it or leave it dead.
        CountDownLatch firstFrame = new CountDownLatch(1);
        CountDownLatch revivedFrame = new CountDownLatch(1);
        List<String> framesOnSecondSocket = new CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();

        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (attempts.incrementAndGet() == 1) {
                    return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                        @Override public void onMessage(WebSocket ws, String text) {
                            if (firstFrame.getCount() > 0) {
                                firstFrame.countDown();
                            } else {
                                ws.close(1000, "bye");
                            }
                        }
                    });
                }
                return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                    @Override public void onMessage(WebSocket ws, String text) {
                        framesOnSecondSocket.add(text);
                        revivedFrame.countDown();
                    }
                });
            }
        });
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).reconnectDelayMs(50).build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.subscribeMarket(List.of("tokA"));
        assertTrue(firstFrame.await(15, TimeUnit.SECONDS), "the initial frame must go out");
        streaming.unsubscribeMarket(List.of("tokA"));

        Thread.sleep(300);
        streaming.subscribeMarket(List.of("tokB"));

        assertTrue(revivedFrame.await(15, TimeUnit.SECONDS),
                "a subscribe after an empty-channel drop must reopen the socket");
        assertTrue(framesOnSecondSocket.get(0).contains("tokB"),
                "the revived socket carries the whole authoritative set: " + framesOnSecondSocket);
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

        gateway = StreamingGateway.builder().wsBase(wsBase()).reconnectDelayMs(50).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.addLifecycleListener(new StreamLifecycleListener() {
            @Override public void onClose(StreamChannel c, long g, int code, String reason) {
                throw new IllegalStateException("boom");
            }
        });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS),
                "reconnect must be scheduled even though the application callback threw");
    }
}
