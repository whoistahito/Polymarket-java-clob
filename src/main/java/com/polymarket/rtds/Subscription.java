package com.polymarket.rtds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymarket.client.ApiKeyCreds;
import java.util.List;

/**
 * A single RTDS subscription configuration (topic + type + optional filters/auth).
 *
 * <p>Build one with the static factories, then serialize a request with
 * {@link #requestJson(String, List)}. Note the server's filter quirk handled in
 * {@link #toNode()}: {@code crypto_prices} sends a raw JSON array,
 * {@code crypto_prices_chainlink} an escaped JSON string.
 */
public final class Subscription {

    static final String TOPIC_CRYPTO = "crypto_prices";
    static final String TOPIC_CHAINLINK = "crypto_prices_chainlink";
    static final String TOPIC_COMMENTS = "comments";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String topic;
    private final String type;
    /** Filters payload: an array (crypto) or object (chainlink), or {@code null}. */
    private final JsonNode filters;
    private final ApiKeyCreds clobAuth;

    private Subscription(String topic, String type, JsonNode filters, ApiKeyCreds clobAuth) {
        this.topic = topic;
        this.type = type;
        this.filters = filters;
        this.clobAuth = clobAuth;
    }

    /**
     * Binance crypto prices. {@code symbols} are lowercase pairs (e.g. {@code "btcusdt"});
     * pass {@code null} or empty to subscribe to all pairs.
     */
    public static Subscription cryptoPrices(List<String> symbols) {
        JsonNode filters = null;
        if (symbols != null && !symbols.isEmpty()) {
            ArrayNode arr = MAPPER.createArrayNode();
            symbols.forEach(arr::add);
            filters = arr;
        }
        return new Subscription(TOPIC_CRYPTO, "update", filters, null);
    }

    /**
     * Chainlink oracle prices. {@code symbol} is slash-separated (e.g. {@code "btc/usd"});
     * pass {@code null} for all symbols.
     */
    public static Subscription chainlinkPrices(String symbol) {
        JsonNode filters = null;
        if (symbol != null) {
            filters = MAPPER.createObjectNode().put("symbol", symbol);
        }
        return new Subscription(TOPIC_CHAINLINK, "*", filters, null);
    }

    /** Comment events; pass {@code null} {@code type} for all comment events ({@code "*"}). */
    public static Subscription comments(CommentType type) {
        return new Subscription(TOPIC_COMMENTS, type == null ? "*" : type.wireValue(), null, null);
    }

    /** Returns a copy of this subscription carrying CLOB auth (for authenticated comments). */
    public Subscription withClobAuth(ApiKeyCreds creds) {
        return new Subscription(topic, type, filters, creds);
    }

    String topic() { return topic; }
    String type()  { return type; }

    /** Serialize this subscription as a JSON node, honoring the filter quirk. */
    ObjectNode toNode() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("topic", topic);
        node.put("type", type);
        if (filters != null) {
            if (TOPIC_CHAINLINK.equals(topic)) {
                node.put("filters", filters.toString()); // escaped JSON string
            } else {
                node.set("filters", filters);            // raw JSON
            }
        }
        if (clobAuth != null) {
            ObjectNode auth = MAPPER.createObjectNode();
            auth.put("key", clobAuth.getKey());
            auth.put("secret", clobAuth.getSecret());
            auth.put("passphrase", clobAuth.getPassphrase());
            node.set("clob_auth", auth);
        }
        return node;
    }

    /** Build a {@code {"action":..,"subscriptions":[..]}} request frame. */
    public static String requestJson(String action, List<Subscription> subs) {
        ObjectNode req = MAPPER.createObjectNode();
        req.put("action", action);
        ArrayNode arr = req.putArray("subscriptions");
        subs.forEach(s -> arr.add(s.toNode()));
        return req.toString();
    }
}
