package com.polymarket.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.ws.model.LastTradePrice;
import com.polymarket.ws.model.OrderMessage;
import com.polymarket.ws.model.WsMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 028 — every documented wire field reaches the consumer.
 *
 * <p>Unknown JSON is discarded silently, so a field that is not modelled is a field the recorder can
 * never audit against. The fixtures below are the complete documented shapes from
 * {@code api-reference/wss/market.md} and {@code api-reference/wss/user.md}, so a future schema
 * change shows up here as a failing assertion rather than as missing data in a replay.
 */
@DisplayName("TC-WSF — documented WebSocket fields (Ticket 028)")
class DocumentedWsFieldsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The complete documented {@code last_trade_price} frame. */
    private static final String LAST_TRADE_PRICE_JSON = """
        {
          "event_type": "last_trade_price",
          "asset_id": "114122071",
          "market": "0x6a67b9d8",
          "price": "0.456",
          "size": "219.217767",
          "fee_rate_bps": "0",
          "side": "BUY",
          "timestamp": "1750428146322",
          "transaction_hash": "0xeeefffggghhh"
        }
        """;

    /** The complete documented user-channel {@code order} frame. */
    private static final String ORDER_JSON = """
        {
          "event_type": "order",
          "id": "0xorder1",
          "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
          "market": "0x5f65177b",
          "asset_id": "71321045",
          "side": "SELL",
          "original_size": "7",
          "size_matched": "4",
          "price": "0.52",
          "type": "UPDATE",
          "order_type": "GTC",
          "status": "LIVE",
          "associate_trades": ["t1", "t2"],
          "expiration": "1735689600",
          "created_at": "1700000000",
          "outcome": "Up",
          "maker_address": "0x1234567890123456789012345678901234567890",
          "timestamp": "1757908892351"
        }
        """;

    @Test
    @DisplayName("TC-WSF-001 the complete last-trade fixture preserves the transaction hash")
    void lastTradePricePreservesTransactionHash() throws Exception {
        WsMessage message = MAPPER.readValue(LAST_TRADE_PRICE_JSON, WsMessage.class);

        LastTradePrice trade = assertInstanceOf(LastTradePrice.class, message);
        assertEquals("0xeeefffggghhh", trade.getTransactionHash());
        assertEquals("114122071", trade.getAssetId());
        assertEquals("0x6a67b9d8", trade.getMarket());
        assertEquals("0.456", trade.getPrice());
        assertEquals("219.217767", trade.getSize());
        assertEquals("0", trade.getFeeRateBps());
        assertEquals("BUY", trade.getSide());
    }

    @Test
    @DisplayName("TC-WSF-002 the last-trade timestamp string is preserved character for character")
    void lastTradeTimestampPreservedExactly() throws Exception {
        LastTradePrice trade =
            (LastTradePrice) MAPPER.readValue(LAST_TRADE_PRICE_JSON, WsMessage.class);

        // Kept as the wire string: parsing to a number and back can lose the exact representation,
        // and consumers order events by receive sequence, not by a reformatted timestamp.
        assertEquals("1750428146322", trade.getTimestamp());
    }

    @Test
    @DisplayName("TC-WSF-003 a last-trade frame without a transaction hash leaves it null")
    void lastTradeMissingHashStaysNull() throws Exception {
        LastTradePrice trade = (LastTradePrice) MAPPER.readValue("""
            {"event_type":"last_trade_price","asset_id":"a1","market":"0xm","price":"0.5",
             "timestamp":"1750428146322"}
            """, WsMessage.class);

        assertNull(trade.getTransactionHash());
        assertNull(trade.getSize());
        assertNull(trade.getSide());
        assertNull(trade.getFeeRateBps());
    }

    @Test
    @DisplayName("TC-WSF-004 the complete order fixture preserves created_at, expiration, "
        + "order_type, and maker_address")
    void orderPreservesDocumentedAuditFields() throws Exception {
        WsMessage message = MAPPER.readValue(ORDER_JSON, WsMessage.class);

        OrderMessage order = assertInstanceOf(OrderMessage.class, message);
        assertEquals("1700000000", order.getCreatedAt());
        assertEquals("1735689600", order.getExpiration());
        assertEquals("GTC", order.getOrderType());
        assertEquals("0x1234567890123456789012345678901234567890", order.getMakerAddress());
    }

    @Test
    @DisplayName("TC-WSF-005 the order fixture still preserves every previously modelled field")
    void orderPreservesExistingFields() throws Exception {
        OrderMessage order = (OrderMessage) MAPPER.readValue(ORDER_JSON, WsMessage.class);

        assertEquals("0xorder1", order.getId());
        assertEquals("0x5f65177b", order.getMarket());
        assertEquals("71321045", order.getAssetId());
        assertEquals("SELL", order.getSide());
        assertEquals("0.52", order.getPrice());
        assertEquals("UPDATE", order.getMsgType());
        assertEquals("LIVE", order.getStatus());
        assertEquals("7", order.getOriginalSize());
        assertEquals("4", order.getSizeMatched());
        assertEquals("Up", order.getOutcome());
        assertEquals("f4f247b7-4ac7-ff29-a152-04fda0a8755a", order.getOwner());
        assertEquals(List.of("t1", "t2"), order.getAssociateTrades());
        assertEquals("1757908892351", order.getTimestamp());
    }

    @Test
    @DisplayName("TC-WSF-006 the four added order fields stay null when the frame omits them")
    void orderMissingAuditFieldsStayNull() throws Exception {
        OrderMessage order = (OrderMessage) MAPPER.readValue("""
            {"event_type":"order","id":"0x1","asset_id":"a1","market":"0xm","side":"BUY",
             "size_matched":"1","type":"PLACEMENT"}
            """, WsMessage.class);

        assertNull(order.getCreatedAt());
        assertNull(order.getExpiration());
        assertNull(order.getOrderType());
        assertNull(order.getMakerAddress());
    }

    @Test
    @DisplayName("TC-WSF-007 a numeric created_at is preserved without inventing a format")
    void numericCreatedAtPreserved() throws Exception {
        OrderMessage order = (OrderMessage) MAPPER.readValue("""
            {"event_type":"order","id":"0x1","asset_id":"a1","market":"0xm","side":"BUY",
             "size_matched":"1","type":"PLACEMENT","created_at":1700000000,
             "expiration":0,"timestamp":1757908892351}
            """, WsMessage.class);

        assertEquals("1700000000", order.getCreatedAt());
        assertEquals("0", order.getExpiration());
        assertEquals("1757908892351", order.getTimestamp());
    }
}
