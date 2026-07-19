package com.polymarket.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.model.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WsClient} and WebSocket message model deserialisation.
 *
 * <p>These tests do not open a real network connection; they exercise the message
 * parsing logic and builder validation in isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TC-WS — WsClient tests")
class WsClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private WsMessageListener noopListener;

    @BeforeEach
    void setUp() {
        noopListener = new WsMessageListener() {
            @Override public void onMessage(WsMessage message) {}
            @Override public void onError(Exception error) {}
            @Override public void onClose(int code, String reason) {}
        };
    }

    // ------------------------------------------------------------------ //
    // Builder                                                              //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-001 builder creates client with defaults")
    void builderDefaults() {
        WsClient client = WsClient.builder()
            .listener(noopListener)
            .build();
        assertNotNull(client);
    }

    @Test
    @DisplayName("TC-WS-002 builder rejects null listener")
    void builderRejectsNullListener() {
        assertThrows(NullPointerException.class, () ->
            WsClient.builder().listener(null).build());
    }

    @Test
    @DisplayName("TC-WS-003 DEFAULT_WS_BASE constant is correct")
    void defaultWsBaseConstant() {
        assertEquals(
            "wss://ws-subscriptions-clob.polymarket.com",
            WsClient.DEFAULT_WS_BASE
        );
    }

    @Test
    @DisplayName("TC-WS-004 subscribeMarket rejects empty list")
    void subscribeMarketRejectsEmpty() {
        WsClient client = WsClient.builder().listener(noopListener).build();
        assertThrows(IllegalArgumentException.class,
            () -> client.subscribeMarket(List.of()));
    }

    @Test
    @DisplayName("TC-WS-005 subscribeUser without credentials throws")
    void subscribeUserWithoutCreds() {
        WsClient client = WsClient.builder().listener(noopListener).build();
        assertThrows(IllegalStateException.class,
            () -> client.subscribeUser(List.of("0xabc")));
    }

    @Test
    @DisplayName("TC-WS-006 subscribeUser with credentials does not throw before connect")
    void subscribeUserWithCredsRequiresWsBase() {
        // Verify the builder wires creds correctly — the WsClient stores them without error
        ApiKeyCreds creds = new ApiKeyCreds("k", "c2VjcmV0", "pass");
        WsClient client = WsClient.builder()
            .listener(noopListener)
            .apiKeyCreds(creds)
            .walletAddress("0x1234")
            // Override to a non-resolvable URL so no real connection is attempted
            .wsBase("wss://127.0.0.1:1")
            .build();
        assertNotNull(client);
        // Actual network call would fail; we only verify builder wiring here
    }

    // ------------------------------------------------------------------ //
    // Message model deserialisation                                        //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-WS-010 deserialise book update")
    void deserialiseBookUpdate() throws Exception {
        String json = """
            {
              "event_type": "book",
              "asset_id": "123",
              "market": "0xabc",
              "timestamp": "1700000000000",
              "bids": [{"price":"0.45","size":"100"}],
              "asks": [{"price":"0.55","size":"200"}],
              "hash": "abc123"
            }
            """;
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(BookUpdate.class, msg);
        BookUpdate book = (BookUpdate) msg;
        assertEquals("123", book.getAssetId());
        assertEquals("0xabc", book.getMarket());
        assertEquals(1, book.getBids().size());
        assertEquals("0.45", book.getBids().get(0).getPrice());
        assertEquals("0.55", book.getAsks().get(0).getPrice());
        assertTrue(book.isMarket());
        assertFalse(book.isUser());
    }

    @Test
    @DisplayName("TC-WS-011 deserialise price_change event")
    void deserialisePriceChange() throws Exception {
        String json = """
            {
              "event_type": "price_change",
              "market": "0xabc",
              "timestamp": "1700000000000",
              "price_changes": [
                {"asset_id":"tok1","price":"0.5","side":"BUY","hash":"h1"}
              ]
            }
            """;
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(PriceChange.class, msg);
        PriceChange pc = (PriceChange) msg;
        assertEquals("0xabc", pc.getMarket());
        assertEquals(1, pc.getPriceChanges().size());
        assertEquals("tok1", pc.getPriceChanges().get(0).getAssetId());
        assertEquals("BUY", pc.getPriceChanges().get(0).getSide());
    }

    @Test
    @DisplayName("TC-WS-012 deserialise tick_size_change event")
    void deserialiseTickSizeChange() throws Exception {
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
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(TickSizeChange.class, msg);
        TickSizeChange tsc = (TickSizeChange) msg;
        assertEquals("0.01", tsc.getOldTickSize());
        assertEquals("0.001", tsc.getNewTickSize());
    }

    @Test
    @DisplayName("TC-WS-013 deserialise last_trade_price event")
    void deserialiseLastTradePrice() throws Exception {
        String json = """
            {
              "event_type": "last_trade_price",
              "asset_id": "tok3",
              "market": "0xaaa",
              "price": "0.62",
              "side": "SELL",
              "timestamp": "1700000000000"
            }
            """;
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(LastTradePrice.class, msg);
        LastTradePrice ltp = (LastTradePrice) msg;
        assertEquals("0.62", ltp.getPrice());
        assertEquals("SELL", ltp.getSide());
    }

    @Test
    @DisplayName("TC-WS-014 deserialise best_bid_ask event")
    void deserialiseBestBidAsk() throws Exception {
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
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(BestBidAsk.class, msg);
        BestBidAsk bba = (BestBidAsk) msg;
        assertEquals("0.48", bba.getBestBid());
        assertEquals("0.52", bba.getBestAsk());
        assertEquals("0.04", bba.getSpread());
    }

    @Test
    @DisplayName("TC-WS-015 deserialise new_market event")
    void deserialiseNewMarket() throws Exception {
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
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(NewMarket.class, msg);
        NewMarket nm = (NewMarket) msg;
        assertEquals("mkt1", nm.getId());
        assertEquals(2, nm.getAssetIds().size());
        assertEquals(List.of("Yes", "No"), nm.getOutcomes());
    }

    @Test
    @DisplayName("TC-WS-016 deserialise market_resolved event")
    void deserialiseMarketResolved() throws Exception {
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
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(MarketResolved.class, msg);
        MarketResolved mr = (MarketResolved) msg;
        assertEquals("tok7", mr.getWinningAssetId());
        assertEquals("Yes", mr.getWinningOutcome());
    }

    @Test
    @DisplayName("TC-WS-017 deserialise trade message (user channel)")
    void deserialiseTradeMessage() throws Exception {
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
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(TradeMessage.class, msg);
        TradeMessage trade = (TradeMessage) msg;
        assertEquals("t1", trade.getId());
        assertEquals("0.7", trade.getPrice());
        assertTrue(trade.isUser());
        assertFalse(trade.isMarket());
    }

    @Test
    @DisplayName("TC-WS-017c deserialise official nested maker-order fields")
    void deserialiseTradeMakerOrder() throws Exception {
        String json = """
            {
              "event_type": "trade",
              "id": "t1",
              "asset_id": "taker-token",
              "side": "BUY",
              "size": "50",
              "status": "MATCHED",
              "trader_side": "MAKER",
              "maker_orders": [{
                "asset_id": "maker-token",
                "matched_amount": "6.66",
                "order_id": "order-1",
                "side": "SELL",
                "owner": "api-key",
                "maker_address": "0xmaker",
                "price": "0.50",
                "fee_rate_bps": "0"
              }]
            }
            """;

        TradeMessage trade = (TradeMessage) MAPPER.readValue(json, WsMessage.class);
        assertEquals("taker-token", trade.getAssetId());
        assertEquals(1, trade.getMakerOrders().size());
        assertEquals("maker-token", trade.getMakerOrders().get(0).getAssetId());
        assertEquals("6.66", trade.getMakerOrders().get(0).getMatchedAmount());
        assertEquals("SELL", trade.getMakerOrders().get(0).getSide());
        assertEquals("0xmaker", trade.getMakerOrders().get(0).getMakerAddress());
        assertEquals("0", trade.getMakerOrders().get(0).getFeeRateBps());
    }

    @Test
    @DisplayName("TC-WS-017a trade match time from live wire key 'matchtime'")
    void deserialiseTradeMatchTimeLiveKey() throws Exception {
        String json = """
            { "event_type": "trade", "id": "t1", "matchtime": "1672290701", "type": "trade" }
            """;
        TradeMessage trade = (TradeMessage) MAPPER.readValue(json, WsMessage.class);
        assertEquals("1672290701", trade.getMatchTime());
    }

    @Test
    @DisplayName("TC-WS-017b trade match time alias 'match_time' still accepted")
    void deserialiseTradeMatchTimeAlias() throws Exception {
        String json = """
            { "event_type": "trade", "id": "t1", "match_time": "1672290701", "type": "trade" }
            """;
        TradeMessage trade = (TradeMessage) MAPPER.readValue(json, WsMessage.class);
        assertEquals("1672290701", trade.getMatchTime());
    }

    @Test
    @DisplayName("TC-WS-018 deserialise order message (user channel)")
    void deserialiseOrderMessage() throws Exception {
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
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(OrderMessage.class, msg);
        OrderMessage order = (OrderMessage) msg;
        assertEquals("o1", order.getId());
        assertEquals("PLACEMENT", order.getMsgType());
        assertTrue(order.isUser());
    }

    @Test
    @DisplayName("TC-WS-019 unknown event_type maps to WsMessage.Unknown")
    void unknownEventType() throws Exception {
        String json = """
            {"event_type":"some_future_type","data":"x"}
            """;
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        assertInstanceOf(WsMessage.Unknown.class, msg);
    }

    @Test
    @DisplayName("TC-WS-020 WsMessage.isUser / isMarket semantics")
    void isUserIsMarketSemantics() throws Exception {
        // Market messages
        for (String eventType : List.of("book", "price_change", "tick_size_change",
            "last_trade_price", "best_bid_ask", "new_market", "market_resolved")) {
            String json = String.format(
                "{\"event_type\":\"%s\",\"market\":\"0x1\",\"timestamp\":\"0\"}", eventType);
            WsMessage msg = MAPPER.readValue(json, WsMessage.class);
            assertFalse(msg.isUser(),   eventType + " should not be user");
            assertTrue(msg.isMarket(),  eventType + " should be market");
        }
        // User messages
        for (String eventType : List.of("trade", "order")) {
            String json = String.format(
                "{\"event_type\":\"%s\",\"id\":\"x\",\"market\":\"0x1\",\"asset_id\":\"a\","
                    + "\"side\":\"BUY\",\"price\":\"0.5\"}", eventType);
            WsMessage msg = MAPPER.readValue(json, WsMessage.class);
            assertTrue(msg.isUser(),    eventType + " should be user");
            assertFalse(msg.isMarket(), eventType + " should not be market");
        }
    }

    // emitMidpointUpdates default/true behavior is covered by
    // WsTypedSubscriptionTest (TC-WS-T-052, TC-WS-T-053) via real dispatch simulation.
}
