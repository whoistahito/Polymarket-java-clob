package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.internal.streaming.StreamingGateway;
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

/** Registration/subscription coverage, ported from the 1.0 {@code WsClient} Ticket 026 behaviour. */
class StreamingRegistrationAndSubscriptionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private List<String> startCapturingServer() throws Exception {
        List<String> frames = new CopyOnWriteArrayList<>();
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { frames.add(text); }
        }));
        server.start();
        return frames;
    }

    private String wsBase() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    @Test
    void shouldSendNoFramesWhenHandlersAreRegisteredBeforeSubscription() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.onBookUpdate(List.of("tokA"), e -> {});
        streaming.onPriceChange(List.of("tokA"), e -> {});
        streaming.onLastTradePrice(List.of("tokA"), e -> {});
        streaming.onTickSizeChange(List.of("tokA"), e -> {});

        Thread.sleep(200);
        assertTrue(frames.isEmpty(), "registration must not open a socket or send a frame: " + frames);
    }

    @Test
    void shouldSendOneInitialFrameWhenOneMarketSubscriptionIsRequested() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.onBookUpdate(List.of("tokA", "tokB"), e -> {});
        streaming.subscribeMarket(List.of("tokA", "tokB"));

        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size(), "expected exactly one frame, got " + frames);
        JsonNode frame = MAPPER.readTree(frames.get(0));
        assertEquals("market", frame.get("type").asText());
        assertTrue(frame.get("initial_dump").asBoolean());
        assertEquals(2, frame.get("assets_ids").size());
    }

    @Test
    void shouldSendOnlyDeltaWhenAddingMarketSubscriptionDynamically() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.subscribeMarket(List.of("tokA"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);
        streaming.subscribeMarket(List.of("tokB"));
        for (int i = 0; i < 50 && frames.size() < 2; i++) Thread.sleep(50);

        assertEquals(2, frames.size(), frames.toString());
        JsonNode update = MAPPER.readTree(frames.get(1));
        assertEquals("subscribe", update.get("operation").asText());
        assertFalse(update.has("initial_dump"));
        assertEquals(1, update.get("assets_ids").size());
        assertEquals("tokB", update.get("assets_ids").get(0).asText());
    }

    @Test
    void shouldDeliverSnapshotWhenHandlerWasRegisteredBeforeSubscription() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        List<BookEvent> books = new CopyOnWriteArrayList<>();

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                ws.send("""
                    {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
                     "bids":[{"price":"0.48","size":"100"}],"asks":[{"price":"0.52","size":"200"}],"hash":"h"}
                    """);
            }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.onBookUpdate(List.of("tokA"), b -> { books.add(b); received.countDown(); });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(received.await(10, TimeUnit.SECONDS), "the snapshot must reach the handler");
        assertEquals("tokA", books.get(0).assetId());
    }

    @Test
    void shouldAccumulateTokensWhenMarketSubscriptionsAreAdded() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.subscribeMarket(List.of("tokA"));
        streaming.subscribeMarket(List.of("tokB"));

        assertEquals(List.of("tokA", "tokB"), streaming.subscribedAssetIds());
    }

    @Test
    void shouldRemoveTokenWhenMarketSubscriptionIsRemoved() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.subscribeMarket(List.of("tokA", "tokB"));
        streaming.unsubscribeMarket(List.of("tokA"));

        assertEquals(List.of("tokB"), streaming.subscribedAssetIds());
    }

    @Test
    void shouldRestoreAccumulatedTokensWhenMarketChannelReconnects() throws Exception {
        List<String> firstConnectionFrames = new CopyOnWriteArrayList<>();
        List<String> reconnectFrames = new CopyOnWriteArrayList<>();
        CountDownLatch reconnected = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                firstConnectionFrames.add(text);
                if (firstConnectionFrames.size() == 2) {
                    ws.close(1000, "server drop");
                }
            }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, okhttp3.Response response) { reconnected.countDown(); }
            @Override public void onMessage(WebSocket ws, String text) { reconnectFrames.add(text); }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).reconnectDelayMs(50).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.subscribeMarket(List.of("tokA"));
        for (int i = 0; i < 100 && firstConnectionFrames.isEmpty(); i++) Thread.sleep(50);
        streaming.subscribeMarket(List.of("tokB"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS), "channel must reconnect");
        for (int i = 0; i < 200 && reconnectFrames.isEmpty(); i++) Thread.sleep(50);

        assertFalse(reconnectFrames.isEmpty(), "the reconnected channel must re-subscribe");
        JsonNode restore = MAPPER.readTree(reconnectFrames.get(0));
        assertTrue(restore.get("initial_dump").asBoolean());
        List<String> restored = new java.util.ArrayList<>();
        restore.get("assets_ids").forEach(n -> restored.add(n.asText()));
        assertEquals(List.of("tokA", "tokB"), restored);
    }

    @Test
    void shouldStopDeliveryWhenRegistrationIsRemoved() throws Exception {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        List<String> seen = new java.util.ArrayList<>();

        Streaming.Registration registration =
                streaming.onBookUpdate(List.of("tokA"), b -> seen.add(b.assetId()));
        registration.remove();
        registration.remove();
        registration.close();

        assertEquals(0, seen.size());
    }

    @Test
    void shouldClearTokensWhenAllMarketSubscriptionsAreRemoved() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.subscribeMarket(List.of("tokA"));
        streaming.unsubscribeMarket(List.of("tokA"));

        assertTrue(streaming.subscribedAssetIds().isEmpty());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenMarketSubscriptionListIsEmpty() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> streaming.subscribeMarket(List.of()));
    }

    @Test
    void shouldSendInitialFrameBeforeUpdatesWhenMarketSubscriptionsAreConcurrent() throws Exception {
        int threads = 8;
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            String assetId = "tok" + i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    streaming.subscribeMarket(List.of(assetId));
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
        assertTrue(done.await(10, TimeUnit.SECONDS), "every subscribing thread must finish");

        List<JsonNode> sent = awaitSubscriptionFrames(frames, 1);
        assertTrue(sent.get(0).path("initial_dump").asBoolean(),
                "the first frame on the wire must be the initial dump, not an update: " + frames);
        assertEquals(1, sent.stream().filter(f -> f.path("initial_dump").asBoolean()).count(),
                "exactly one initial dump: " + frames);

        List<String> onTheWire = new java.util.ArrayList<>();
        sent.forEach(f -> f.get("assets_ids").forEach(n -> onTheWire.add(n.asText())));
        assertEquals(streaming.subscribedAssetIds().size(), onTheWire.size(),
                "no asset may be requested twice: " + frames);
        assertEquals(new java.util.HashSet<>(streaming.subscribedAssetIds()), new java.util.HashSet<>(onTheWire));
    }

    @Test
    void shouldSetCustomEventsFlagWhenInitialMarketFrameIsSent() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());

        streaming.enableCustomMarketEvents();
        streaming.subscribeMarket(List.of("tokA"));
        List<JsonNode> sent = awaitSubscriptionFrames(frames, 1);

        JsonNode initial = sent.get(0);
        assertTrue(initial.get("custom_feature_enabled").asBoolean());
        List<String> documented = StreamProtocol.fieldsOf("marketChannel", "subscriptionRequestFields");
        initial.fieldNames().forEachRemaining(name ->
                assertTrue(documented.contains(name), name + " is not a documented request field: " + initial));
    }

    @Test
    void shouldIncludeNewTokenInRestoreWhenSubscribedDuringReconnect() throws Exception {
        List<String> first = new CopyOnWriteArrayList<>();
        List<String> second = new CopyOnWriteArrayList<>();
        CountDownLatch dropped = new CountDownLatch(1);
        CountDownLatch reopened = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                first.add(text);
                ws.close(1000, "server drop");
                dropped.countDown();
            }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, okhttp3.Response response) { reopened.countDown(); }
            @Override public void onMessage(WebSocket ws, String text) { second.add(text); }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).reconnectDelayMs(2_000).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.subscribeMarket(List.of("tokA"));
        assertTrue(dropped.await(20, TimeUnit.SECONDS), "the first socket must drop");
        streaming.subscribeMarket(List.of("tokB"));

        assertTrue(reopened.await(30, TimeUnit.SECONDS), "the channel must reconnect");
        List<JsonNode> restored = awaitSubscriptionFrames(second, 1);
        assertEquals(1, restored.size(), "exactly one frame restores the Authoritative Subscription: " + second);
        assertTrue(restored.get(0).path("initial_dump").asBoolean());
        List<String> ids = new java.util.ArrayList<>();
        restored.get(0).get("assets_ids").forEach(n -> ids.add(n.asText()));
        assertEquals(List.of("tokA", "tokB"), ids);
    }

    private static List<JsonNode> awaitSubscriptionFrames(List<String> frames, int atLeast) throws Exception {
        for (int i = 0; i < 100 && frames.size() < atLeast; i++) Thread.sleep(50);
        Thread.sleep(300);
        List<JsonNode> parsed = new java.util.ArrayList<>();
        for (String frame : frames) {
            if (!"PING".equals(frame)) parsed.add(MAPPER.readTree(frame));
        }
        return parsed;
    }
}
