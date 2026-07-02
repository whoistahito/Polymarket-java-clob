package com.polymarket.ws;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.model.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for WsClient typed per-subscription callbacks (Rust parity).
 *
 * <p>These tests verify the typed registration methods ({@code onBookUpdate},
 * {@code onPriceChange}, etc.) without opening a real WebSocket connection.
 * The private {@code dispatch(String)} method is invoked via reflection to
 * simulate incoming server frames.
 */
@DisplayName("TC-WS-T — WsClient typed subscriptions (Rust parity)")
class WsTypedSubscriptionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Book JSON used across several tests
    private static final String BOOK_JSON = """
        {
          "event_type": "book",
          "asset_id": "tok1",
          "market": "0xabc",
          "timestamp": "1700000000000",
          "bids": [{"price":"0.48","size":"100"}],
          "asks": [{"price":"0.52","size":"200"}],
          "hash": "h1"
        }
        """;

    // ------------------------------------------------------------------ //
    // Helpers                                                              //
    // ------------------------------------------------------------------ //

    /** Build a WsClient without a listener (defaults to NOOP) at a non-resolvable URL. */
    private static WsClient noSocketClient() {
        return WsClient.builder()
            .wsBase("wss://127.0.0.1:1")
            .build();
    }

    /** Build a WsClient with user-auth creds at a non-resolvable URL. */
    private static WsClient authClient() {
        ApiKeyCreds creds = new ApiKeyCreds("key", "c2VjcmV0", "pass");
        return WsClient.builder()
            .wsBase("wss://127.0.0.1:1")
            .apiKeyCreds(creds)
            .walletAddress("0x1234")
            .build();
    }

    /**
     * Invoke WsClient's private {@code dispatch(String)} method via reflection.
     * This simulates a raw text frame arriving from the server.
     */
    private static void simulateFrame(WsClient client, String json) throws Exception {
        Method dispatch = WsClient.class.getDeclaredMethod("dispatch", String.class);
        dispatch.setAccessible(true);
        dispatch.invoke(client, json);
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-001 — Builder without .listener() succeeds (NOOP default)   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-001 WsClient can be built without explicit listener")
    void builderWithoutListenerSucceeds() {
        WsClient client = WsClient.builder().wsBase("wss://127.0.0.1:1").build();
        assertNotNull(client);
    }

    @Test
    @DisplayName("TC-WS-T-002 builder still rejects explicit null listener")
    void builderExplicitNullListenerThrows() {
        assertThrows(NullPointerException.class, () ->
            WsClient.builder().listener(null).build());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-010 — onBookUpdate                                          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-010 onBookUpdate callback receives BookUpdate")
    void onBookUpdateCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<BookUpdate> received = new AtomicReference<>();

        client.onBookUpdate(List.of("tok1"), received::set);
        simulateFrame(client, BOOK_JSON);

        assertNotNull(received.get());
        assertEquals("tok1", received.get().getAssetId());
        assertEquals("0xabc", received.get().getMarket());
    }

    @Test
    @DisplayName("TC-WS-T-011 onBookUpdate null callback throws NullPointerException")
    void onBookUpdateNullCallbackThrows() {
        WsClient client = noSocketClient();
        assertThrows(NullPointerException.class,
            () -> client.onBookUpdate(List.of("tok1"), null));
    }

    @Test
    @DisplayName("TC-WS-T-012 multiple onBookUpdate callbacks all invoked")
    void multipleBookUpdateCallbacks() throws Exception {
        WsClient client = noSocketClient();
        List<BookUpdate> updates = new ArrayList<>();

        client.onBookUpdate(List.of("tok1"), updates::add);
        client.onBookUpdate(List.of("tok1"), updates::add);
        simulateFrame(client, BOOK_JSON);

        assertEquals(2, updates.size(), "Both callbacks should be called");
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-020 — onPriceChange                                         //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-020 onPriceChange callback receives PriceChange")
    void onPriceChangeCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<PriceChange> received = new AtomicReference<>();

        client.onPriceChange(List.of("tok1"), received::set);

        String json = """
            {
              "event_type": "price_change",
              "market": "0xabc",
              "timestamp": "1700000000000",
              "price_changes": [{"asset_id":"tok1","price":"0.5","side":"BUY","hash":"h1"}]
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("0xabc", received.get().getMarket());
        assertEquals(1, received.get().getPriceChanges().size());
        assertEquals("0.5", received.get().getPriceChanges().get(0).getPrice());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-030 — onLastTradePrice                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-030 onLastTradePrice callback receives LastTradePrice")
    void onLastTradePriceCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<LastTradePrice> received = new AtomicReference<>();

        client.onLastTradePrice(List.of("tok1"), received::set);

        String json = """
            {
              "event_type": "last_trade_price",
              "asset_id": "tok1",
              "market": "0xabc",
              "price": "0.63",
              "side": "SELL",
              "timestamp": "1700000000000"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("0.63", received.get().getPrice());
        assertEquals("SELL", received.get().getSide());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-040 — onTickSizeChange                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-040 onTickSizeChange callback receives TickSizeChange")
    void onTickSizeChangeCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<TickSizeChange> received = new AtomicReference<>();

        client.onTickSizeChange(List.of("tok2"), received::set);

        String json = """
            {
              "event_type": "tick_size_change",
              "asset_id": "tok2",
              "market": "0xdef",
              "old_tick_size": "0.01",
              "new_tick_size": "0.001",
              "timestamp": "1700000000000"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("0.01", received.get().getOldTickSize());
        assertEquals("0.001", received.get().getNewTickSize());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-050 — onMidpointUpdate                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-050 onMidpointUpdate callback derives midpoint from BookUpdate")
    void onMidpointUpdateCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<MidpointUpdate> received = new AtomicReference<>();

        client.onMidpointUpdate(List.of("tok1"), received::set);
        simulateFrame(client, BOOK_JSON); // bids[0]=0.48, asks[0]=0.52 → mid=0.50

        assertNotNull(received.get(), "MidpointUpdate should be derived from BookUpdate");
        assertEquals("tok1", received.get().getAssetId());
        assertEquals("0xabc", received.get().getMarket());
        // midpoint = (0.48 + 0.52) / 2 = 0.50 (6dp precision)
        assertEquals("0.500000", received.get().getMidpoint());
    }

    @Test
    @DisplayName("TC-WS-T-051 onMidpointUpdate does not emit if only asks (no bids)")
    void onMidpointUpdateSkipsIfNoBids() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<MidpointUpdate> received = new AtomicReference<>();

        client.onMidpointUpdate(List.of("tok1"), received::set);

        String json = """
            {
              "event_type": "book",
              "asset_id": "tok1",
              "market": "0xabc",
              "timestamp": "1700000000000",
              "bids": [],
              "asks": [{"price":"0.52","size":"200"}],
              "hash": "h1"
            }
            """;
        simulateFrame(client, json);

        assertNull(received.get(), "No midpoint emitted without both bid and ask");
    }

    @Test
    @DisplayName("TC-WS-T-052 emitMidpointUpdates(true) still delivers MidpointUpdate to main listener")
    void emitMidpointUpdatesMainListenerStillReceives() throws Exception {
        List<WsMessage> received = new ArrayList<>();
        WsClient client = WsClient.builder()
            .wsBase("wss://127.0.0.1:1")
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage m) { received.add(m); }
                @Override public void onError(Exception e) {}
                @Override public void onClose(int c, String r) {}
            })
            .emitMidpointUpdates(true)
            .build();

        simulateFrame(client, BOOK_JSON);

        // Expect both BookUpdate and MidpointUpdate
        assertTrue(received.stream().anyMatch(m -> m instanceof BookUpdate));
        assertTrue(received.stream().anyMatch(m -> m instanceof MidpointUpdate));
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-060 — onBestBidAsk                                          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-060 onBestBidAsk callback receives BestBidAsk")
    void onBestBidAskCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<BestBidAsk> received = new AtomicReference<>();

        client.onBestBidAsk(List.of("tok4"), received::set);

        String json = """
            {
              "event_type": "best_bid_ask",
              "market": "0xbbb",
              "asset_id": "tok4",
              "best_bid": "0.48",
              "best_ask": "0.52",
              "spread": "0.04",
              "timestamp": "1700000000000"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("0.48", received.get().getBestBid());
        assertEquals("0.52", received.get().getBestAsk());
        assertEquals("0.04", received.get().getSpread());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-070 — onNewMarket                                           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-070 onNewMarket callback receives NewMarket")
    void onNewMarketCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<NewMarket> received = new AtomicReference<>();

        client.onNewMarket(List.of("tok5"), received::set);

        String json = """
            {
              "event_type": "new_market",
              "id": "mkt1",
              "question": "Will X happen?",
              "market": "0xccc",
              "slug": "will-x-happen",
              "description": "desc",
              "assets_ids": ["tok5","tok6"],
              "outcomes": ["Yes","No"],
              "timestamp": "1700000000000"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("mkt1", received.get().getId());
        assertEquals(2, received.get().getAssetIds().size());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-080 — onMarketResolved                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-080 onMarketResolved callback receives MarketResolved")
    void onMarketResolvedCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<MarketResolved> received = new AtomicReference<>();

        client.onMarketResolved(List.of("tok7"), received::set);

        String json = """
            {
              "event_type": "market_resolved",
              "id": "mkt2",
              "market": "0xddd",
              "assets_ids": ["tok7","tok8"],
              "outcomes": ["Yes","No"],
              "winning_asset_id": "tok7",
              "winning_outcome": "Yes",
              "timestamp": "1700000000000"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("tok7", received.get().getWinningAssetId());
        assertEquals("Yes", received.get().getWinningOutcome());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-090 — onTrade (user channel)                                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-090 onTrade callback receives TradeMessage")
    void onTradeCallback() throws Exception {
        WsClient client = authClient();
        AtomicReference<TradeMessage> received = new AtomicReference<>();

        client.onTrade(List.of("0xeee"), received::set);

        String json = """
            {
              "event_type": "trade",
              "id": "t1",
              "market": "0xeee",
              "asset_id": "tok9",
              "side": "BUY",
              "size": "50",
              "price": "0.7",
              "status": "MATCHED",
              "type": "trade"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("t1", received.get().getId());
        assertEquals("0.7", received.get().getPrice());
    }

    @Test
    @DisplayName("TC-WS-T-091 onTrade without auth throws IllegalStateException")
    void onTradeWithoutAuthThrows() {
        WsClient client = noSocketClient();
        assertThrows(IllegalStateException.class,
            () -> client.onTrade(List.of("0xeee"), t -> {}));
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-100 — onOrder (user channel)                                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-100 onOrder callback receives OrderMessage")
    void onOrderCallback() throws Exception {
        WsClient client = authClient();
        AtomicReference<OrderMessage> received = new AtomicReference<>();

        client.onOrder(List.of("0xfff"), received::set);

        String json = """
            {
              "event_type": "order",
              "id": "o1",
              "market": "0xfff",
              "asset_id": "tok10",
              "side": "SELL",
              "price": "0.3",
              "type": "PLACEMENT",
              "status": "OPEN"
            }
            """;
        simulateFrame(client, json);

        assertNotNull(received.get());
        assertEquals("o1", received.get().getId());
        assertEquals("PLACEMENT", received.get().getMsgType());
    }

    @Test
    @DisplayName("TC-WS-T-101 onOrder without auth throws IllegalStateException")
    void onOrderWithoutAuthThrows() {
        WsClient client = noSocketClient();
        assertThrows(IllegalStateException.class,
            () -> client.onOrder(List.of("0xfff"), o -> {}));
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-110 — onUserEvent (user channel)                            //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-110 onUserEvent receives both TradeMessage and OrderMessage")
    void onUserEventReceivesBoth() throws Exception {
        WsClient client = authClient();
        List<WsMessage> received = new ArrayList<>();

        client.onUserEvent(List.of("0xeee"), received::add);

        String tradeJson = """
            {
              "event_type": "trade",
              "id": "t2",
              "market": "0xeee",
              "asset_id": "tok9",
              "side": "SELL",
              "size": "10",
              "price": "0.5",
              "status": "MATCHED",
              "type": "trade"
            }
            """;
        String orderJson = """
            {
              "event_type": "order",
              "id": "o2",
              "market": "0xeee",
              "asset_id": "tok9",
              "side": "BUY",
              "price": "0.5",
              "type": "PLACEMENT",
              "status": "OPEN"
            }
            """;
        simulateFrame(client, tradeJson);
        simulateFrame(client, orderJson);

        assertEquals(2, received.size());
        assertTrue(received.stream().anyMatch(m -> m instanceof TradeMessage));
        assertTrue(received.stream().anyMatch(m -> m instanceof OrderMessage));
    }

    @Test
    @DisplayName("TC-WS-T-111 onUserEvent without auth throws IllegalStateException")
    void onUserEventWithoutAuthThrows() {
        WsClient client = noSocketClient();
        assertThrows(IllegalStateException.class,
            () -> client.onUserEvent(List.of("0xeee"), m -> {}));
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-120 — Typed callbacks and main listener both fire           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-120 typed callback and main listener both receive BookUpdate")
    void typedCallbackAndListenerBothFire() throws Exception {
        List<WsMessage> listenerMsgs = new ArrayList<>();
        List<BookUpdate> typedMsgs = new ArrayList<>();

        WsClient client = WsClient.builder()
            .wsBase("wss://127.0.0.1:1")
            .listener(new WsMessageListener() {
                @Override public void onMessage(WsMessage m) { listenerMsgs.add(m); }
                @Override public void onError(Exception e) {}
                @Override public void onClose(int c, String r) {}
            })
            .build();

        client.onBookUpdate(List.of("tok1"), typedMsgs::add);
        simulateFrame(client, BOOK_JSON);

        assertEquals(1, listenerMsgs.size(), "Main listener should receive the message");
        assertEquals(1, typedMsgs.size(), "Typed callback should also receive the message");
        assertSame(listenerMsgs.get(0), typedMsgs.get(0), "Both should receive the same instance");
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-130 — Wrong event type does NOT reach unrelated callbacks   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-130 PriceChange frame does not trigger onBookUpdate callback")
    void wrongTypeDoesNotTriggerCallback() throws Exception {
        WsClient client = noSocketClient();
        AtomicReference<BookUpdate> received = new AtomicReference<>();

        client.onBookUpdate(List.of("tok1"), received::set);

        String json = """
            {
              "event_type": "price_change",
              "market": "0xabc",
              "timestamp": "1700000000000",
              "price_changes": [{"asset_id":"tok1","price":"0.5","side":"BUY","hash":"h1"}]
            }
            """;
        simulateFrame(client, json);

        assertNull(received.get(), "onBookUpdate should not be triggered by price_change events");
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-140 — Batched frames (JSON array)                           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-140 batched JSON array dispatches each element to typed callbacks")
    void batchedFrameDispatchesAll() throws Exception {
        WsClient client = noSocketClient();
        List<BookUpdate> updates = new ArrayList<>();

        client.onBookUpdate(List.of("tok1", "tok2"), updates::add);

        String batch = """
            [
              {
                "event_type": "book",
                "asset_id": "tok1",
                "market": "0xaaa",
                "timestamp": "1",
                "bids": [{"price":"0.4","size":"10"}],
                "asks": [{"price":"0.6","size":"10"}],
                "hash": "h1"
              },
              {
                "event_type": "book",
                "asset_id": "tok2",
                "market": "0xbbb",
                "timestamp": "2",
                "bids": [{"price":"0.3","size":"5"}],
                "asks": [{"price":"0.7","size":"5"}],
                "hash": "h2"
              }
            ]
            """;
        simulateFrame(client, batch);

        assertEquals(2, updates.size(), "Both books in the batch should reach the callback");
        assertEquals("tok1", updates.get(0).getAssetId());
        assertEquals("tok2", updates.get(1).getAssetId());
    }

    // ------------------------------------------------------------------ //
    // TC-WS-T-150 — onOrder and onTrade independently target right type   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-T-150 onOrder does not trigger for TradeMessage frames")
    void onOrderDoesNotTriggerForTrades() throws Exception {
        WsClient client = authClient();
        List<OrderMessage> orders = new ArrayList<>();
        client.onOrder(List.of("0xeee"), orders::add);

        String tradeJson = """
            {
              "event_type": "trade",
              "id": "t3",
              "market": "0xeee",
              "asset_id": "tok9",
              "side": "BUY",
              "size": "5",
              "price": "0.5",
              "status": "MATCHED",
              "type": "trade"
            }
            """;
        simulateFrame(client, tradeJson);

        assertTrue(orders.isEmpty(), "onOrder callback must not be triggered by trade frames");
    }

    @Test
    @DisplayName("TC-WS-T-151 onTrade does not trigger for OrderMessage frames")
    void onTradeDoesNotTriggerForOrders() throws Exception {
        WsClient client = authClient();
        List<TradeMessage> trades = new ArrayList<>();
        client.onTrade(List.of("0xfff"), trades::add);

        String orderJson = """
            {
              "event_type": "order",
              "id": "o3",
              "market": "0xfff",
              "asset_id": "tok10",
              "side": "BUY",
              "price": "0.4",
              "type": "PLACEMENT",
              "status": "OPEN"
            }
            """;
        simulateFrame(client, orderJson);

        assertTrue(trades.isEmpty(), "onTrade callback must not be triggered by order frames");
    }
}
