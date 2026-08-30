package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.internal.streaming.RtdsGateway;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TC-RR — registration is separate from subscription, authoritative sets survive a reconnect, and
 * the wire frames use the currently documented RTDS filter formats.
 */
@DisplayName("TC-RR — Rtds registration and subscription")
class RtdsRegistrationAndSubscriptionTest {

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

    @Test
    @DisplayName("TC-RR-001 registrations before subscribing send zero frames")
    void registrationsSendNoFrames() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        rtds.onBinancePrice(List.of(), e -> {});
        rtds.onChainlinkPrice(List.of(), e -> {});
        rtds.onCommentCreated(e -> {});

        Thread.sleep(200);
        assertTrue(frames.isEmpty(), "registration must not open a socket or send a frame: " + frames);
    }

    @Test
    @DisplayName("TC-RR-002 Binance filters are the documented comma-separated symbol string")
    void binanceFiltersAreCommaSeparated() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        rtds.subscribeBinancePrices(List.of("btcusdt", "ethusdt"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size());
        JsonNode subs = MAPPER.readTree(frames.get(0)).get("subscriptions");
        assertEquals("crypto_prices", subs.get(0).get("topic").asText());
        assertEquals("update", subs.get(0).get("type").asText());
        assertEquals("btcusdt,ethusdt", subs.get(0).get("filters").asText());
    }

    @Test
    @DisplayName("TC-RR-003 Chainlink filters are the documented escaped single-symbol JSON string")
    void chainlinkFiltersAreJsonStrings() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        rtds.subscribeChainlinkPrices(List.of("eth/usd"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size());
        JsonNode entry = MAPPER.readTree(frames.get(0)).get("subscriptions").get(0);
        assertEquals("crypto_prices_chainlink", entry.get("topic").asText());
        assertEquals("*", entry.get("type").asText());
        JsonNode filter = MAPPER.readTree(entry.get("filters").asText());
        assertEquals("eth/usd", filter.get("symbol").asText());
    }

    @Test
    @DisplayName("TC-RR-004 an entity-filtered comment subscription carries the documented official filter")
    void commentEntityFilterMatchesDocumentedShape() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        rtds.subscribeComments(CommentEventType.COMMENT_CREATED, RtdsEntityType.EVENT, 18396);
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size());
        JsonNode entry = MAPPER.readTree(frames.get(0)).get("subscriptions").get(0);
        assertEquals("comments", entry.get("topic").asText());
        assertEquals("comment_created", entry.get("type").asText());
        JsonNode filter = MAPPER.readTree(entry.get("filters").asText());
        assertEquals(18396, filter.get("parentEntityID").asInt());
        assertEquals("Event", filter.get("parentEntityType").asText());
    }

    @Test
    @DisplayName("TC-RR-005 an unfiltered comment subscription omits the filters field")
    void unfilteredCommentSubscriptionHasNoFilters() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        rtds.subscribeComments(CommentEventType.REACTION_REMOVED);
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size());
        JsonNode entry = MAPPER.readTree(frames.get(0)).get("subscriptions").get(0);
        assertEquals("reaction_removed", entry.get("type").asText());
        assertFalse(entry.has("filters"));
    }

    @Test
    @DisplayName("TC-RR-006 a dynamic add sends only the delta, not the whole authoritative set again")
    void dynamicAddIsDeltaOnly() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);

        rtds.subscribeBinancePrices(List.of("btcusdt"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);
        rtds.subscribeBinancePrices(List.of("ethusdt"));
        for (int i = 0; i < 50 && frames.size() < 2; i++) Thread.sleep(50);

        assertEquals(2, frames.size(), frames.toString());
        JsonNode second = MAPPER.readTree(frames.get(1)).get("subscriptions").get(0);
        assertEquals("ethusdt", second.get("filters").asText());
    }

    @Test
    @DisplayName("TC-RR-007 subscribe A then B leaves an authoritative set of A+B")
    void subscribeAccumulatesSymbols() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);

        rtds.subscribeBinancePrices(List.of("btcusdt"));
        rtds.subscribeBinancePrices(List.of("ethusdt"));

        assertEquals(List.of("btcusdt", "ethusdt"), rtds.subscribedBinanceSymbols());
    }

    @Test
    @DisplayName("TC-RR-008 unsubscribing removes only that symbol from the authoritative set")
    void unsubscribeRemovesFromSet() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);

        rtds.subscribeBinancePrices(List.of("btcusdt", "ethusdt"));
        rtds.unsubscribeBinancePrices(List.of("btcusdt"));

        assertEquals(List.of("ethusdt"), rtds.subscribedBinanceSymbols());
    }

    @Test
    @DisplayName("TC-RR-009 reconnect restores the accumulated authoritative state across all three streams")
    void reconnectRestoresAccumulatedState() throws Exception {
        List<String> firstConnectionFrames = new CopyOnWriteArrayList<>();
        List<String> reconnectFrames = new CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch reconnected = new java.util.concurrent.CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                firstConnectionFrames.add(text);
                ws.close(1000, "server drop");
            }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, okhttp3.Response response) { reconnected.countDown(); }
            @Override public void onMessage(WebSocket ws, String text) { reconnectFrames.add(text); }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));
        rtds.subscribeChainlinkPrices(List.of("eth/usd"));
        rtds.subscribeComments(CommentEventType.COMMENT_CREATED);

        assertTrue(reconnected.await(20, java.util.concurrent.TimeUnit.SECONDS), "channel must reconnect");
        for (int i = 0; i < 200 && reconnectFrames.isEmpty(); i++) Thread.sleep(50);

        assertFalse(reconnectFrames.isEmpty(), "the reconnected channel must re-subscribe");
        JsonNode restored = MAPPER.readTree(reconnectFrames.get(0)).get("subscriptions");
        assertEquals(3, restored.size(), restored.toString());
    }

    @Test
    @DisplayName("TC-RR-010 a removal handle stops delivery, idempotently")
    void removalHandleStopsDelivery() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);
        List<String> seen = new java.util.ArrayList<>();

        Rtds.Registration registration = rtds.onBinancePrice(List.of(), e -> seen.add(e.symbol()));
        registration.remove();
        registration.remove(); // idempotent
        registration.close(); // AutoCloseable alias

        assertEquals(0, seen.size());
    }

    @Test
    @DisplayName("TC-RR-011 subscribeBinancePrices rejects an empty list")
    void subscribeBinanceRejectsEmpty() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);
        Assertions.assertThrows(IllegalArgumentException.class, () -> rtds.subscribeBinancePrices(List.of()));
    }

    @Test
    @DisplayName("TC-RR-012 subscribeChainlinkPrices rejects an empty list")
    void subscribeChainlinkRejectsEmpty() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);
        Assertions.assertThrows(IllegalArgumentException.class, () -> rtds.subscribeChainlinkPrices(List.of()));
    }
}
