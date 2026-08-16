package com.polymarket.internal.markets;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.OrderBookSource;
import com.polymarket.markets.Price;
import com.polymarket.markets.PriceLevel;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CLOB transport for live books. Every rule comes from the same response, so nothing is
 * cached and no Gamma value is ever substituted.
 */
public final class OrderBookGateway implements OrderBookSource {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    private final PolymarketConfig config;
    private final HttpRuntime runtime;

    public OrderBookGateway(PolymarketConfig config, HttpRuntime runtime) {
        this.config = config;
        this.runtime = runtime;
    }

    @Override
    public Optional<OrderBookSnapshot> book(TokenId token) throws IOException {
        HttpOutcome outcome = get("/book?token_id=" + token.value());
        // A 404 is an answer: the exchange keeps no book for that token.
        if (outcome.status() == 404) return Optional.empty();
        return Optional.of(snapshot(runtime.parse(require(outcome).body())));
    }

    /**
     * Batched through the GET form of {@code /books}: the documented POST form is a read wearing
     * a write's method, and the write path is deliberately never retried.
     */
    @Override
    public List<OrderBookSnapshot> books(List<TokenId> tokens) throws IOException {
        // Token ids are validated digits, so the comma-separated list needs no escaping.
        HttpOutcome outcome = get("/books?token_ids="
                + tokens.stream().map(TokenId::value).collect(Collectors.joining(",")));
        List<OrderBookSnapshot> books = new ArrayList<>();
        runtime.parse(require(outcome).body()).forEach(node -> books.add(snapshot(node)));
        return List.copyOf(books);
    }

    private HttpOutcome get(String path) throws IOException {
        return runtime.get(config.clobHost(), path, ACCEPT_JSON);
    }

    private static HttpOutcome require(HttpOutcome outcome) throws IOException {
        if (!outcome.successful()) {
            throw new IOException("book read failed with HTTP " + outcome.status());
        }
        return outcome;
    }

    private static OrderBookSnapshot snapshot(JsonNode node) {
        return new OrderBookSnapshot(
                node.path("market").asText(),
                new TokenId(node.path("asset_id").asText()),
                observedAt(node),
                node.path("hash").asText(),
                levels(node.get("bids")),
                levels(node.get("asks")),
                new MarketRules(TickSize.of(node.path("tick_size").asText()),
                        ShareQuantity.of(node.path("min_order_size").asText()),
                        node.path("neg_risk").asBoolean()),
                text(node, "last_trade_price").map(Price::of).orElse(null));
    }

    /** The book timestamp is unix milliseconds; the spec's ten-digit example is a placeholder. */
    private static Instant observedAt(JsonNode node) {
        return Instant.ofEpochMilli(Long.parseLong(node.path("timestamp").asText()));
    }

    private static List<PriceLevel> levels(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        List<PriceLevel> levels = new ArrayList<>();
        array.forEach(level -> levels.add(new PriceLevel(
                Price.of(level.path("price").asText()),
                ShareQuantity.of(level.path("size").asText()))));
        return levels;
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? Optional.empty() : Optional.of(value.asText());
    }
}
