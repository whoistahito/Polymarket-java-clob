package com.polymarket.internal.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.streaming.BinancePriceEvent;
import com.polymarket.streaming.ChainlinkPriceEvent;
import com.polymarket.streaming.CommentCreatedEvent;
import com.polymarket.streaming.CommentProfile;
import com.polymarket.streaming.CommentRemovedEvent;
import com.polymarket.streaming.ReactionCreatedEvent;
import com.polymarket.streaming.ReactionRemovedEvent;
import com.polymarket.streaming.RtdsEntityType;
import com.polymarket.streaming.RtdsEventSink;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps raw RTDS WebSocket JSON into the immutable {@code streaming} records, field by field like
 * {@code StreamEventMapper} — a public record with a Jackson annotation would fail the boundary test.
 */
final class RtdsEventMapper {

    private static final Logger log = LoggerFactory.getLogger(RtdsEventMapper.class);

    private final ObjectMapper mapper;

    RtdsEventMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** {@code equity_prices} and undocumented topics are out of this issue's scope. */
    void dispatch(String text, RtdsEventSink sink) {
        if (text == null) return;
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
            log.warn("Failed to parse RTDS frame: {}", text, e);
        }
    }

    private void dispatchNode(JsonNode node, RtdsEventSink sink) {
        String topic = node.path("topic").asText("");
        String type = node.path("type").asText("");
        JsonNode payload = node.path("payload");
        // The envelope timestamp is when RTDS observed the event; it is a separate fact from the
        // payload's own time, and the only stream-side ordering the caller ever gets.
        long observedAt = node.path("timestamp").asLong(0);
        switch (topic) {
            case "crypto_prices" ->
                    sink.onBinancePrice(toPriceEvent(payload, observedAt, BinancePriceEvent::new));
            case "crypto_prices_chainlink" ->
                    sink.onChainlinkPrice(toPriceEvent(payload, observedAt, ChainlinkPriceEvent::new));
            case "comments" -> dispatchComment(type, payload, observedAt, sink);
            default -> log.debug("Ignoring undocumented or unrecognised RTDS topic: {}", node);
        }
    }

    private void dispatchComment(String type, JsonNode p, long observedAt, RtdsEventSink sink) {
        switch (type) {
            case "comment_created" -> sink.onCommentCreated(toCommentCreated(p, observedAt));
            case "comment_removed" -> sink.onCommentRemoved(toCommentRemoved(p, observedAt));
            case "reaction_created" -> sink.onReactionCreated(toReactionCreated(p, observedAt));
            case "reaction_removed" -> sink.onReactionRemoved(toReactionRemoved(p, observedAt));
            default -> log.debug("Ignoring unrecognised RTDS comment event type: {}", type);
        }
    }

    private interface PriceEventFactory<T> {
        T create(String symbol, long observedAt, long timestamp, BigDecimal value);
    }

    private static <T> T toPriceEvent(JsonNode n, long observedAt, PriceEventFactory<T> factory) {
        return factory.create(text(n, "symbol"), observedAt,
                n.path("timestamp").asLong(0), decimal(n, "value"));
    }

    private static CommentCreatedEvent toCommentCreated(JsonNode n, long observedAt) {
        return new CommentCreatedEvent(text(n, "id"), observedAt, optText(n, "body"),
                entityType(n), entityId(n), optText(n, "parentCommentID"),
                optText(n, "userAddress"), optText(n, "replyAddress"), optText(n, "createdAt"),
                optText(n, "updatedAt"), optLong(n, "reactionCount"), optLong(n, "reportCount"),
                profile(n.get("profile")));
    }

    private static CommentRemovedEvent toCommentRemoved(JsonNode n, long observedAt) {
        return new CommentRemovedEvent(text(n, "id"), observedAt, optText(n, "body"),
                entityType(n), entityId(n), optText(n, "userAddress"));
    }

    private static ReactionCreatedEvent toReactionCreated(JsonNode n, long observedAt) {
        return new ReactionCreatedEvent(text(n, "id"), observedAt, optLong(n, "commentID"),
                optText(n, "reactionType"), optText(n, "icon"), optText(n, "userAddress"),
                optText(n, "createdAt"), profile(n.get("profile")));
    }

    private static ReactionRemovedEvent toReactionRemoved(JsonNode n, long observedAt) {
        return new ReactionRemovedEvent(text(n, "id"), observedAt, optLong(n, "commentID"),
                optText(n, "reactionType"), optText(n, "userAddress"), profile(n.get("profile")));
    }

    private static Optional<CommentProfile> profile(JsonNode n) {
        if (n == null || n.isNull()) return Optional.empty();
        return Optional.of(new CommentProfile(text(n, "baseAddress"), n.path("displayUsernamePublic").asBoolean(false),
                text(n, "name"), text(n, "proxyWallet"), text(n, "pseudonym")));
    }

    private static Optional<RtdsEntityType> entityType(JsonNode n) {
        return RtdsEntityType.fromWireValue(text(n, "parentEntityType"));
    }

    private static Optional<Long> entityId(JsonNode n) {
        return optLong(n, "parentEntityID");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Optional<String> optText(JsonNode node, String field) {
        return Optional.ofNullable(text(node, field));
    }

    private static Optional<Long> optLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.asLong());
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
