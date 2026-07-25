package com.polymarket.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.LastTradePrice;
import com.polymarket.ws.model.OrderMessage;
import com.polymarket.ws.model.PriceChange;
import com.polymarket.ws.model.TickSizeChange;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 026 — callback registration is separate from subscription.
 *
 * <p>Every typed callback used to send its own subscribe frame, so registering four handlers sent
 * four frames and asked for four initial dumps. Worse, a handler registered after the first frame
 * could miss the snapshot it was registered to receive. Registration is now purely local; exactly
 * one explicit subscribe opens the channel and requests the dump.
 */
@DisplayName("TC-WSR — registration vs subscription (Ticket 026)")
class WsRegistrationAndSubscriptionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BOOK_A = """
        {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
         "bids":[{"price":"0.48","size":"100"}],"asks":[{"price":"0.52","size":"200"}],"hash":"h"}
        """;
    private static final String BOOK_B = """
        {"event_type":"book","asset_id":"tokB","market":"0xm","timestamp":"1",
         "bids":[{"price":"0.45","size":"10"}],"asks":[{"price":"0.55","size":"20"}],"hash":"h"}
        """;

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

    /** A client pointed at an unroutable address: registration must not need a live socket. */
    private static WsClient offlineClient() {
        return WsClient.builder().wsBase("wss://127.0.0.1:1").build();
    }

    private static void dispatch(WsClient client, String json) throws Exception {
        Method dispatch = WsClient.class.getDeclaredMethod("dispatch", String.class);
        dispatch.setAccessible(true);
        dispatch.invoke(client, json);
    }

    /** Collects every text frame the client sends to a real (mock) WebSocket server. */
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

    // ------------------------------------------------------------------ //
    // Registration performs no network action                             //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSR-001 four callback registrations send zero frames")
    void registrationsSendNoFrames() throws Exception {
        List<String> frames = startCapturingServer();
        client = WsClient.builder().wsBase(wsBase()).build();

        client.registerBookUpdates(List.of("tokA"), b -> {});
        client.registerPriceChanges(List.of("tokA"), p -> {});
        client.registerLastTradePrices(List.of("tokA"), t -> {});
        client.registerTickSizeChanges(List.of("tokA"), t -> {});

        Thread.sleep(200);
        assertTrue(frames.isEmpty(), "registration must not open a socket or send a frame: " + frames);
        assertFalse(client.isMarketConnected());
    }

    @Test
    @DisplayName("TC-WSR-002 one explicit subscribe sends exactly one initial frame")
    void oneSubscribeSendsOneInitialFrame() throws Exception {
        List<String> frames = startCapturingServer();
        client = WsClient.builder().wsBase(wsBase()).build();

        client.registerBookUpdates(List.of("tokA", "tokB"), b -> {});
        client.registerPriceChanges(List.of("tokA", "tokB"), p -> {});
        client.registerLastTradePrices(List.of("tokA", "tokB"), t -> {});
        client.registerTickSizeChanges(List.of("tokA", "tokB"), t -> {});
        client.subscribeMarket(List.of("tokA", "tokB"));

        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size(), "expected exactly one frame, got " + frames);
        JsonNode frame = MAPPER.readTree(frames.get(0));
        assertEquals("market", frame.get("type").asText());
        assertTrue(frame.get("initial_dump").asBoolean(), "the initial frame must request the dump");
        assertEquals(2, frame.get("assets_ids").size());
    }

    @Test
    @DisplayName("TC-WSR-003 a dynamic add is an update frame, not a second initial dump")
    void dynamicAddIsNotAnInitialDump() throws Exception {
        List<String> frames = startCapturingServer();
        client = WsClient.builder().wsBase(wsBase()).build();

        client.subscribeMarket(List.of("tokA"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);
        client.subscribeMarket(List.of("tokB"));
        for (int i = 0; i < 50 && frames.size() < 2; i++) Thread.sleep(50);

        assertEquals(2, frames.size(), frames.toString());
        JsonNode update = MAPPER.readTree(frames.get(1));
        assertEquals("subscribe", update.get("operation").asText());
        assertFalse(update.has("initial_dump"),
            "a dynamic update must not re-request the initial dump: " + frames.get(1));
        assertEquals(1, update.get("assets_ids").size(), "only the delta is sent");
        assertEquals("tokB", update.get("assets_ids").get(0).asText());
    }

    // ------------------------------------------------------------------ //
    // Per-callback token filtering                                        //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSR-004 token A callbacks do not receive token B events")
    void callbacksAreFilteredByToken() throws Exception {
        client = offlineClient();
        List<String> seenByA = new ArrayList<>();
        List<String> seenByB = new ArrayList<>();

        client.registerBookUpdates(List.of("tokA"), b -> seenByA.add(b.getAssetId()));
        client.registerBookUpdates(List.of("tokB"), b -> seenByB.add(b.getAssetId()));

        dispatch(client, BOOK_A);
        dispatch(client, BOOK_B);

        assertEquals(List.of("tokA"), seenByA);
        assertEquals(List.of("tokB"), seenByB);
    }

    @Test
    @DisplayName("TC-WSR-005 an empty filter receives every token")
    void emptyFilterReceivesEverything() throws Exception {
        client = offlineClient();
        List<String> seen = new ArrayList<>();

        client.registerBookUpdates(List.of(), b -> seen.add(b.getAssetId()));
        dispatch(client, BOOK_A);
        dispatch(client, BOOK_B);

        assertEquals(List.of("tokA", "tokB"), seen);
    }

    @Test
    @DisplayName("TC-WSR-006 price-change batches are filtered per entry asset")
    void priceChangeFilteredPerEntry() throws Exception {
        client = offlineClient();
        List<PriceChange> seenByA = new ArrayList<>();
        List<PriceChange> seenByB = new ArrayList<>();

        client.registerPriceChanges(List.of("tokA"), seenByA::add);
        client.registerPriceChanges(List.of("tokB"), seenByB::add);

        dispatch(client, """
            {"event_type":"price_change","market":"0xm","timestamp":"1",
             "price_changes":[{"asset_id":"tokA","price":"0.5","size":"200","side":"BUY"}]}
            """);

        assertEquals(1, seenByA.size());
        assertTrue(seenByB.isEmpty(), "a batch touching only tokA must not reach a tokB handler");
    }

    @Test
    @DisplayName("TC-WSR-007 last-trade and tick-size callbacks are filtered by token too")
    void otherMarketCallbacksFiltered() throws Exception {
        client = offlineClient();
        List<LastTradePrice> trades = new ArrayList<>();
        List<TickSizeChange> ticks = new ArrayList<>();

        client.registerLastTradePrices(List.of("tokA"), trades::add);
        client.registerTickSizeChanges(List.of("tokA"), ticks::add);

        dispatch(client, """
            {"event_type":"last_trade_price","asset_id":"tokB","market":"0xm","price":"0.4",
             "timestamp":"1"}
            """);
        dispatch(client, """
            {"event_type":"tick_size_change","asset_id":"tokB","market":"0xm",
             "old_tick_size":"0.01","new_tick_size":"0.001","timestamp":"1"}
            """);

        assertTrue(trades.isEmpty());
        assertTrue(ticks.isEmpty());
    }

    @Test
    @DisplayName("TC-WSR-008 user callbacks are filtered by market condition ID")
    void userCallbacksFilteredByMarket() throws Exception {
        client = WsClient.builder()
            .wsBase("wss://127.0.0.1:1")
            .apiKeyCreds(new ApiKeyCreds("key", "c2VjcmV0", "pass"))
            .walletAddress("0x1")
            .build();
        List<OrderMessage> forMarket1 = new ArrayList<>();
        List<OrderMessage> forMarket2 = new ArrayList<>();

        client.registerOrders(List.of("0xm1"), forMarket1::add);
        client.registerOrders(List.of("0xm2"), forMarket2::add);

        dispatch(client, """
            {"event_type":"order","id":"0x1","market":"0xm1","asset_id":"tokA","side":"BUY",
             "size_matched":"1","type":"UPDATE"}
            """);

        assertEquals(1, forMarket1.size());
        assertTrue(forMarket2.isEmpty());
    }

    // ------------------------------------------------------------------ //
    // Removal handles                                                     //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSR-009 a removal handle stops delivery without touching the socket")
    void removalHandleStopsDelivery() throws Exception {
        client = offlineClient();
        List<String> seen = new ArrayList<>();

        WsClient.Registration registration =
            client.registerBookUpdates(List.of("tokA"), b -> seen.add(b.getAssetId()));

        dispatch(client, BOOK_A);
        registration.remove();
        dispatch(client, BOOK_A);

        assertEquals(1, seen.size(), "no events after removal");
    }

    @Test
    @DisplayName("TC-WSR-010 removal is idempotent and usable as a try-with-resources close")
    void removalIsIdempotent() throws Exception {
        client = offlineClient();
        List<String> seen = new ArrayList<>();

        WsClient.Registration registration =
            client.registerBookUpdates(List.of("tokA"), b -> seen.add(b.getAssetId()));
        registration.remove();
        registration.remove();
        registration.close();

        dispatch(client, BOOK_A);
        assertTrue(seen.isEmpty());
    }

    @Test
    @DisplayName("TC-WSR-011 one callback throwing does not stop the others")
    void oneThrowingCallbackDoesNotStopOthers() throws Exception {
        client = offlineClient();
        List<String> seen = new ArrayList<>();

        client.registerBookUpdates(List.of("tokA"), b -> { throw new IllegalStateException("boom"); });
        client.registerBookUpdates(List.of("tokA"), b -> seen.add(b.getAssetId()));

        dispatch(client, BOOK_A);

        assertEquals(List.of("tokA"), seen);
    }

    // ------------------------------------------------------------------ //
    // Authoritative token set                                             //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WSR-012 subscribe A then B leaves an authoritative set of A+B")
    void subscribeAccumulatesTokens() {
        client = offlineClient();

        client.subscribeMarket(List.of("tokA"));
        client.subscribeMarket(List.of("tokB"));

        assertEquals(List.of("tokA", "tokB"), client.getSubscribedAssetIds());
        assertEquals(2, client.getSubscriptionCount());
    }

    @Test
    @DisplayName("TC-WSR-013 unsubscribing A leaves only B in the authoritative set")
    void unsubscribeRemovesFromSet() {
        client = offlineClient();

        client.subscribeMarket(List.of("tokA", "tokB"));
        client.unsubscribeMarket(List.of("tokA"));

        assertEquals(List.of("tokB"), client.getSubscribedAssetIds());
    }

    @Test
    @DisplayName("TC-WSR-014 re-subscribing a token does not duplicate it")
    void resubscribeDoesNotDuplicate() {
        client = offlineClient();

        client.subscribeMarket(List.of("tokA"));
        client.subscribeMarket(List.of("tokA", "tokB"));

        assertEquals(List.of("tokA", "tokB"), client.getSubscribedAssetIds());
    }

    @Test
    @DisplayName("TC-WSR-015 reconnect restores the accumulated set, not just the last subscribe")
    void reconnectRestoresAccumulatedSet() throws Exception {
        List<String> firstConnectionFrames = new CopyOnWriteArrayList<>();
        List<String> reconnectFrames = new CopyOnWriteArrayList<>();
        CountDownLatch reconnected = new CountDownLatch(1);

        server = new MockWebServer();
        // First connection: accept both subscription frames, then drop the socket from the SERVER
        // side. A client-initiated close never completes its handshake against MockWebServer's
        // default listener, so the client would never observe the disconnect.
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                firstConnectionFrames.add(text);
                if (firstConnectionFrames.size() == 2) {
                    ws.close(1000, "server drop");
                }
            }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { reconnected.countDown(); }
            @Override public void onMessage(WebSocket ws, String text) { reconnectFrames.add(text); }
        }));
        server.start();

        client = WsClient.builder().wsBase(wsBase()).reconnectDelayMs(50).build();
        client.subscribeMarket(List.of("tokA"));
        for (int i = 0; i < 100 && firstConnectionFrames.isEmpty(); i++) Thread.sleep(50);
        client.subscribeMarket(List.of("tokB"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS), "channel must reconnect");
        for (int i = 0; i < 200 && reconnectFrames.isEmpty(); i++) Thread.sleep(50);

        assertFalse(reconnectFrames.isEmpty(), "the reconnected channel must re-subscribe");
        JsonNode restore = MAPPER.readTree(reconnectFrames.get(0));
        assertTrue(restore.get("initial_dump").asBoolean(),
            "a reconnect must request fresh snapshots");
        List<String> restored = new ArrayList<>();
        restore.get("assets_ids").forEach(n -> restored.add(n.asText()));
        assertEquals(List.of("tokA", "tokB"), restored);
    }

    @Test
    @DisplayName("TC-WSR-016 subscribing with no tokens left does not reopen the channel")
    void unsubscribingEverythingStopsReconnect() {
        client = offlineClient();

        client.subscribeMarket(List.of("tokA"));
        client.unsubscribeMarket(List.of("tokA"));

        assertTrue(client.getSubscribedAssetIds().isEmpty());
        assertEquals(0, client.getSubscriptionCount());
    }

    @Test
    @DisplayName("TC-WSR-017 handlers registered before subscribing receive the first snapshot")
    void handlersRegisteredBeforeSubscribeSeeTheSnapshot() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        List<BookUpdate> books = new CopyOnWriteArrayList<>();

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                ws.send(BOOK_A); // the server's initial dump, answered immediately
            }
        }));
        server.start();

        client = WsClient.builder().wsBase(wsBase()).build();
        client.registerBookUpdates(List.of("tokA"), b -> { books.add(b); received.countDown(); });
        client.subscribeMarket(List.of("tokA"));

        assertTrue(received.await(10, TimeUnit.SECONDS), "the snapshot must reach the handler");
        assertEquals("tokA", books.get(0).getAssetId());
    }
}
