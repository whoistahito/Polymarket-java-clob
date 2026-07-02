package com.polymarket.rtds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Top-level RTDS message: {@code {topic, type, timestamp, payload}}.
 *
 * <p>Mirrors the Rust SDK {@code RtdsMessage}. The {@code payload} is left as a
 * raw {@link JsonNode}; use {@link #asCryptoPrice()}, {@link #asChainlinkPrice()},
 * or {@link #asComment()} to decode it based on {@link #topic()}.
 */
public record RtdsMessage(
    String topic,
    String type,
    long timestamp,
    JsonNode payload
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Decode the payload as a Binance crypto price, if this is a {@code crypto_prices} message. */
    public Optional<CryptoPrice> asCryptoPrice() {
        return convert(Subscription.TOPIC_CRYPTO, CryptoPrice.class);
    }

    /** Decode the payload as a Chainlink price, if this is a {@code crypto_prices_chainlink} message. */
    public Optional<ChainlinkPrice> asChainlinkPrice() {
        return convert(Subscription.TOPIC_CHAINLINK, ChainlinkPrice.class);
    }

    /** Decode the payload as a comment event, if this is a {@code comments} message. */
    public Optional<Comment> asComment() {
        return convert(Subscription.TOPIC_COMMENTS, Comment.class);
    }

    private <T> Optional<T> convert(String expectedTopic, Class<T> type) {
        if (!expectedTopic.equals(topic) || payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(MAPPER.treeToValue(payload, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Parse one or more messages from a raw WebSocket frame.
     *
     * <p>Handles both single objects and arrays. Empty / whitespace-only frames
     * (server keepalives) yield an empty list.
     */
    public static List<RtdsMessage> parse(String text) {
        if (text == null) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(trimmed);
            if (root.isArray()) {
                List<RtdsMessage> out = new java.util.ArrayList<>(root.size());
                for (JsonNode node : root) {
                    out.add(MAPPER.treeToValue(node, RtdsMessage.class));
                }
                return out;
            }
            return List.of(MAPPER.treeToValue(root, RtdsMessage.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse RTDS message: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ //
    // Payload types                                                        //
    // ------------------------------------------------------------------ //

    /** Binance crypto price payload. {@code symbol} is lowercase (e.g. {@code "btcusdt"}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CryptoPrice(String symbol, long timestamp, BigDecimal value) {}

    /** Chainlink oracle price payload. {@code symbol} is slash-separated (e.g. {@code "btc/usd"}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChainlinkPrice(String symbol, long timestamp, BigDecimal value) {}

    /** Comment event payload. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Comment(
        String id,
        String body,
        // ponytail: keep ISO-8601 timestamp as String — parse to Instant at the call site if needed.
        @JsonProperty("createdAt") String createdAt,
        @JsonProperty("parentCommentID") String parentCommentId,
        @JsonProperty("parentEntityID") long parentEntityId,
        @JsonProperty("parentEntityType") String parentEntityType,
        CommentProfile profile,
        @JsonProperty("reactionCount") long reactionCount,
        @JsonProperty("replyAddress") String replyAddress,
        @JsonProperty("reportCount") long reportCount,
        @JsonProperty("userAddress") String userAddress
    ) {}

    /** Profile of a comment author. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommentProfile(
        @JsonProperty("baseAddress") String baseAddress,
        @JsonProperty("displayUsernamePublic") boolean displayUsernamePublic,
        String name,
        @JsonProperty("proxyWallet") String proxyWallet,
        String pseudonym
    ) {}
}
