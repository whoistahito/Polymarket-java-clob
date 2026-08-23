package com.polymarket.internal.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.streaming.BestBidAskEvent;
import com.polymarket.streaming.BookEvent;
import com.polymarket.streaming.BookLevel;
import com.polymarket.streaming.LastTradePriceEvent;
import com.polymarket.streaming.MakerOrder;
import com.polymarket.streaming.MarketResolvedEvent;
import com.polymarket.streaming.NewMarketEvent;
import com.polymarket.streaming.OrderEvent;
import com.polymarket.streaming.ParentEventInfo;
import com.polymarket.streaming.PriceChangeEntry;
import com.polymarket.streaming.PriceChangeEvent;
import com.polymarket.streaming.StreamEventSink;
import com.polymarket.streaming.TickSizeChangeEvent;
import com.polymarket.streaming.TradeEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps raw CLOB WebSocket JSON into the immutable {@code streaming} records, field by field like
 * {@code OrderBookGateway} — a public record with a Jackson annotation would fail the boundary test.
 */
final class StreamEventMapper {

    private static final Logger log = LoggerFactory.getLogger(StreamEventMapper.class);

    private final ObjectMapper mapper;

    StreamEventMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    void dispatch(String text, StreamEventSink sink) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "PING".equalsIgnoreCase(trimmed) || "PONG".equalsIgnoreCase(trimmed)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(trimmed);
            if (root.isArray()) {
                root.forEach(node -> dispatchNode(node, sink));
            } else {
                dispatchNode(root, sink);
            }
        } catch (Exception e) {
            log.warn("Failed to parse stream frame: {}", text, e);
        }
    }

    private void dispatchNode(JsonNode node, StreamEventSink sink) {
        switch (node.path("event_type").asText("")) {
            case "book" -> sink.onBook(toBookEvent(node));
            case "price_change" -> sink.onPriceChange(toPriceChangeEvent(node));
            case "last_trade_price" -> sink.onLastTradePrice(toLastTradePriceEvent(node));
            case "tick_size_change" -> sink.onTickSizeChange(toTickSizeChangeEvent(node));
            case "best_bid_ask" -> sink.onBestBidAsk(toBestBidAskEvent(node));
            case "new_market" -> sink.onNewMarket(toNewMarketEvent(node));
            case "market_resolved" -> sink.onMarketResolved(toMarketResolvedEvent(node));
            case "order" -> sink.onOrder(toOrderEvent(node));
            case "trade" -> sink.onTrade(toTradeEvent(node));
            default -> log.debug("Ignoring undocumented or unrecognised event_type: {}", node);
        }
    }

    private static BookEvent toBookEvent(JsonNode n) {
        return new BookEvent(text(n, "asset_id"), text(n, "market"), text(n, "timestamp"),
                levels(n.get("bids")), levels(n.get("asks")), text(n, "hash"));
    }

    private static List<BookLevel> levels(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<BookLevel> levels = new ArrayList<>();
        array.forEach(level -> levels.add(new BookLevel(decimal(level, "price"), decimal(level, "size"))));
        return levels;
    }

    private static PriceChangeEvent toPriceChangeEvent(JsonNode n) {
        List<PriceChangeEntry> entries = new ArrayList<>();
        JsonNode array = n.get("price_changes");
        if (array != null && array.isArray()) {
            array.forEach(e -> entries.add(new PriceChangeEntry(
                    text(e, "asset_id"), decimal(e, "price"), optDecimal(e, "size"), text(e, "side"),
                    optText(e, "hash"), optDecimal(e, "best_bid"), optDecimal(e, "best_ask"))));
        }
        return new PriceChangeEvent(text(n, "market"), text(n, "timestamp"), entries);
    }

    private static LastTradePriceEvent toLastTradePriceEvent(JsonNode n) {
        return new LastTradePriceEvent(text(n, "asset_id"), text(n, "market"), decimal(n, "price"),
                optText(n, "side"), optDecimal(n, "size"), optText(n, "fee_rate_bps"),
                text(n, "timestamp"), optText(n, "transaction_hash"));
    }

    private static TickSizeChangeEvent toTickSizeChangeEvent(JsonNode n) {
        return new TickSizeChangeEvent(text(n, "asset_id"), text(n, "market"),
                decimal(n, "old_tick_size"), decimal(n, "new_tick_size"), text(n, "timestamp"));
    }

    private static BestBidAskEvent toBestBidAskEvent(JsonNode n) {
        return new BestBidAskEvent(text(n, "asset_id"), text(n, "market"), decimal(n, "best_bid"),
                decimal(n, "best_ask"), decimal(n, "spread"), text(n, "timestamp"));
    }

    private static NewMarketEvent toNewMarketEvent(JsonNode n) {
        return new NewMarketEvent(text(n, "id"), text(n, "question"), text(n, "market"), text(n, "slug"),
                optText(n, "description"), strings(n.get("assets_ids")), strings(n.get("outcomes")),
                parentEvent(n.get("event_message")), text(n, "timestamp"), strings(n.get("tags")),
                optText(n, "condition_id"), optBoolean(n, "active"), strings(n.get("clob_token_ids")),
                optText(n, "sports_market_type"), optText(n, "line"), optText(n, "game_start_time"),
                optDecimal(n, "order_price_min_tick_size"), optText(n, "group_item_title"));
    }

    private static MarketResolvedEvent toMarketResolvedEvent(JsonNode n) {
        return new MarketResolvedEvent(text(n, "id"), text(n, "market"), strings(n.get("assets_ids")),
                text(n, "winning_asset_id"), text(n, "winning_outcome"), parentEvent(n.get("event_message")),
                text(n, "timestamp"), strings(n.get("tags")));
    }

    private static Optional<ParentEventInfo> parentEvent(JsonNode n) {
        if (n == null || n.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new ParentEventInfo(optText(n, "id"), optText(n, "ticker"), optText(n, "slug"),
                optText(n, "title"), optText(n, "description")));
    }

    private static OrderEvent toOrderEvent(JsonNode n) {
        return new OrderEvent(text(n, "id"), text(n, "market"), text(n, "asset_id"), text(n, "side"),
                decimal(n, "price"), text(n, "type"), optText(n, "outcome"), optText(n, "owner"),
                optText(n, "order_owner"), optDecimal(n, "original_size"), optDecimal(n, "size_matched"),
                optText(n, "timestamp"), strings(n.get("associate_trades")), optText(n, "status"),
                optText(n, "created_at"), optText(n, "expiration"), optText(n, "order_type"),
                optText(n, "maker_address"));
    }

    private static TradeEvent toTradeEvent(JsonNode n) {
        return new TradeEvent(text(n, "id"), text(n, "market"), text(n, "asset_id"), text(n, "side"),
                decimal(n, "size"), decimal(n, "price"), text(n, "status"), text(n, "type"),
                optText(n, "last_update"), matchTime(n), optText(n, "timestamp"),
                optText(n, "outcome"), optText(n, "owner"), optText(n, "trade_owner"),
                optText(n, "maker_address"), optText(n, "taker_order_id"), makerOrders(n.get("maker_orders")),
                optText(n, "fee_rate_bps"), optText(n, "transaction_hash"), optInt(n, "bucket_index"),
                optText(n, "trader_side"));
    }

    private static List<MakerOrder> makerOrders(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<MakerOrder> makers = new ArrayList<>();
        array.forEach(m -> makers.add(new MakerOrder(text(m, "asset_id"), optDecimal(m, "matched_amount"),
                text(m, "order_id"), optText(m, "outcome"), optInt(m, "outcome_index"), text(m, "side"),
                optText(m, "owner"), optText(m, "maker_address"), decimal(m, "price"),
                optText(m, "fee_rate_bps"))));
        return makers;
    }

    private static List<String> strings(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        array.forEach(v -> out.add(v.asText()));
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Optional<String> optText(JsonNode node, String field) {
        return Optional.ofNullable(text(node, field));
    }

    /** Live wire key is {@code matchtime}; {@code match_time} is accepted as an alias. */
    private static Optional<String> matchTime(JsonNode node) {
        String value = text(node, "matchtime");
        return Optional.ofNullable(value != null ? value : text(node, "match_time"));
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static Optional<Integer> optInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.asInt());
    }

    private static Optional<Boolean> optBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.asBoolean());
    }

    private static Optional<BigDecimal> optDecimal(JsonNode node, String field) {
        return Optional.ofNullable(decimal(node, field));
    }
}
