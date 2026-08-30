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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TC-RA — the RTDS Authoritative Subscription reaches the wire in one initial frame, and closing
 * the capability is terminal and releases every owned resource (issue #23).
 */
@DisplayName("TC-RA — Rtds authoritative subscription and owned lifecycle")
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
                // teardown only
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

    /** Every symbol named in any "subscribe" frame, in wire order, with duplicates preserved. */
    private static List<String> subscribedSymbols(List<String> frames) throws Exception {
        List<String> seen = new ArrayList<>();
        for (String frame : frames) {
            JsonNode msg = MAPPER.readTree(frame);
            if (!"subscribe".equals(msg.path("action").asText())) continue;
            for (JsonNode entry : msg.path("subscriptions")) {
                String filters = entry.path("filters").asText("");
                if (!filters.isBlank() && !filters.startsWith("{")) {
                    for (String symbol : filters.split(",")) seen.add(symbol.trim());
                }
            }
        }
        return seen;
    }

    @Test
    @DisplayName("TC-RA-001 concurrent subscribes cannot overtake or duplicate the initial frame")
    void concurrentSubscribesCannotOvertakeTheInitialFrame() throws Exception {
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

        List<String> seen = subscribedSymbols(frames);
        assertEquals(seen.size(), seen.stream().distinct().count(),
                "a subject was subscribed twice — an update duplicated the initial frame: " + frames);
        assertEquals(rtds.subscribedBinanceSymbols().size(), seen.size(),
                "the wire must carry exactly the Authoritative Subscription: " + frames);
    }

    @Test
    @DisplayName("TC-RA-002 closing is terminal — a later subscribe cannot reopen the socket")
    void closingIsTerminal() throws Exception {
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
    @DisplayName("TC-RA-003 closing releases the socket, keepalive, scheduler, dispatcher and pool")
    void closingReleasesEveryOwnedResource() throws Exception {
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
    @DisplayName("TC-RA-004 a frame arriving after close reaches no application callback")
    void callbackWorkStopsOnClose() throws Exception {
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
