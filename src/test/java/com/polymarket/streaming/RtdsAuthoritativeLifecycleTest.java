package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.internal.streaming.RtdsGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Covers the RTDS authoritative subscription and owned-resource lifecycle contract (issue #23). */
class RtdsAuthoritativeLifecycleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private List<String> startCapturingServer() throws Exception {
        List<String> frames = new CopyOnWriteArrayList<>();
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { frames.add(text); }
        }));
        server.start();
        return frames;
    }

    @Test
    void shouldSendInitialFrameBeforeUpdatesWhenBinanceSubscriptionsAreConcurrent() throws Exception {
        int threads = 8;
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            String symbol = "sym" + i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    rtds.subscribeBinancePrices(List.of(symbol));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "subscribing threads did not finish");
        Thread.sleep(500);

        assertFalse(frames.isEmpty(), "no frame reached the wire");
        JsonNode first = MAPPER.readTree(frames.get(0));
        assertEquals("subscribe", first.path("action").asText(),
                "the first frame on the wire must be the initial authoritative subscribe: " + frames);

        List<JsonNode> binanceEntries = new ArrayList<>();
        for (JsonNode entry : first.path("subscriptions")) {
            if ("crypto_prices".equals(entry.path("topic").asText())) {
                binanceEntries.add(entry);
            }
        }
        assertEquals(1, binanceEntries.size(),
                "all concurrent Binance symbols must share one topic entry: " + frames);
        assertFalse(binanceEntries.get(0).has("filters"),
                "the multi-symbol Binance topic must be unfiltered: " + frames);

        List<String> authoritative = rtds.subscribedBinanceSymbols();
        assertEquals(threads, authoritative.size(),
                "every concurrent symbol must remain in the authoritative set");
        assertEquals(authoritative.size(), authoritative.stream().distinct().count(),
                "the authoritative set must not duplicate a concurrent symbol");
    }

    @Test
    void shouldRestoreMultipleBinanceSymbolsAsOneUnfilteredTopicWhenRtdsReconnects()
            throws Exception {
        List<String> firstConnectionFrames = new CopyOnWriteArrayList<>();
        List<String> reconnectFrames = new CopyOnWriteArrayList<>();
        CountDownLatch reconnected = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                firstConnectionFrames.add(text);
                ws.close(1000, "server drop");
            }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, okhttp3.Response response) {
                reconnected.countDown();
            }

            @Override public void onMessage(WebSocket ws, String text) {
                reconnectFrames.add(text);
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt", "ethusdt"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS), "channel must reconnect");
        for (int i = 0; i < 200 && reconnectFrames.isEmpty(); i++) Thread.sleep(50);

        assertFalse(reconnectFrames.isEmpty(), "the reconnected channel must re-subscribe");
        JsonNode restored = MAPPER.readTree(reconnectFrames.get(0));
        assertEquals("subscribe", restored.path("action").asText());
        JsonNode subscriptions = restored.path("subscriptions");
        assertEquals(1, subscriptions.size(), subscriptions.toString());
        JsonNode binance = subscriptions.get(0);
        assertEquals("crypto_prices", binance.path("topic").asText());
        assertEquals("update", binance.path("type").asText());
        assertFalse(binance.has("filters"), "the restored multi-symbol topic must be unfiltered");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenSubscriptionsFollowClose() throws Exception {
        startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));
        for (int i = 0; i < 100 && server.getRequestCount() < 1; i++) Thread.sleep(50);

        rtds.close();
        int handshakes = server.getRequestCount();

        assertThrows(IllegalStateException.class, () -> rtds.subscribeBinancePrices(List.of("ethusdt")));
        assertThrows(IllegalStateException.class, () -> rtds.subscribeChainlinkPrices(List.of("ethusd")));
        assertThrows(IllegalStateException.class, () -> rtds.subscribeComments(CommentEventType.COMMENT_CREATED));
        Thread.sleep(300);
        assertEquals(handshakes, server.getRequestCount(), "a closed Rtds reopened its socket");
        assertTrue(rtds.isClosed());
    }

    @Test
    void shouldReleaseOwnedResourcesWhenRtdsCloses() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).pingIntervalMs(60).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));
        for (int i = 0; i < 100 && frames.size() < 3; i++) Thread.sleep(50);
        assertTrue(frames.size() >= 2, "the text keepalive never started: " + frames);

        rtds.close();
        gateway.close();
        Thread.sleep(300);
        int afterClose = frames.size();
        Thread.sleep(400);

        assertEquals(afterClose, frames.size(), "the text keepalive kept ticking after close");
        assertTrue(gateway.isClosed(), "the gateway must report its owned resources released");
        assertEquals(0, gateway.connectionPoolSize(), "the connection pool was not evicted");
    }

    @Test
    void shouldStopCallbacksWhenRtdsCloses() throws Exception {
        List<String> delivered = new CopyOnWriteArrayList<>();
        List<WebSocket> sockets = new CopyOnWriteArrayList<>();
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, okhttp3.Response r) { sockets.add(ws); }
        }));
        server.start();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.onCommentCreated(e -> delivered.add(e.id()));
        rtds.subscribeComments(CommentEventType.COMMENT_CREATED);
        for (int i = 0; i < 100 && sockets.isEmpty(); i++) Thread.sleep(50);

        rtds.close();
        sockets.get(0).send("{\"topic\":\"comments\",\"type\":\"comment_created\",\"payload\":{\"id\":\"c1\"}}");
        Thread.sleep(300);

        assertTrue(delivered.isEmpty(), "a closed Rtds still delivered an event: " + delivered);
    }
}
