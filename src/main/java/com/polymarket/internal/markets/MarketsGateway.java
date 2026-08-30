package com.polymarket.internal.markets;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.DiscoveredEvent;
import com.polymarket.markets.DiscoveredMarket;
import com.polymarket.markets.EventQuery;
import com.polymarket.markets.MarketCatalog;
import com.polymarket.markets.MarketMetadata;
import com.polymarket.markets.MarketOutcome;
import com.polymarket.markets.MarketPricing;
import com.polymarket.markets.MarketQuery;
import com.polymarket.markets.MarketSeries;
import com.polymarket.markets.MarketState;
import com.polymarket.markets.MarketTag;
import com.polymarket.markets.SearchResults;
import com.polymarket.markets.Sport;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Gamma transport for discovery; maps wire JSON to domain values and nothing else. */
public final class MarketsGateway implements MarketCatalog {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    private final PolymarketConfig config;
    private final HttpRuntime runtime;

    public MarketsGateway(PolymarketConfig config, HttpRuntime runtime) {
        this.config = config;
        this.runtime = runtime;
    }

    @Override
    public List<DiscoveredEvent> events(EventQuery query) throws IOException {
        QueryString params = new QueryString();
        query.limit().ifPresent(v -> params.add("limit", v.toString()));
        query.offset().ifPresent(v -> params.add("offset", v.toString()));
        query.order().ifPresent(v -> params.add("order", v));
        query.ascending().ifPresent(v -> params.add("ascending", v.toString()));
        query.active().ifPresent(v -> params.add("active", v.toString()));
        query.closed().ifPresent(v -> params.add("closed", v.toString()));
        query.tagSlug().ifPresent(v -> params.add("tag_slug", v));
        List<DiscoveredEvent> events = new ArrayList<>();
        for (JsonNode node : read("/events" + params)) {
            events.add(event(node));
        }
        return List.copyOf(events);
    }

    @Override
    public Optional<DiscoveredEvent> eventBySlug(String slug) throws IOException {
        Optional<JsonNode> node = readOptional("/events/slug/" + encode(slug));
        return node.isPresent() ? Optional.of(event(node.get())) : Optional.empty();
    }

    @Override
    public List<DiscoveredMarket> markets(MarketQuery query) throws IOException {
        QueryString params = new QueryString();
        query.limit().ifPresent(v -> params.add("limit", v.toString()));
        query.closed().ifPresent(v -> params.add("closed", v.toString()));
        List<DiscoveredMarket> markets = new ArrayList<>();
        for (JsonNode node : read("/markets" + params)) {
            markets.add(market(node));
        }
        return List.copyOf(markets);
    }

    @Override
    public Optional<DiscoveredMarket> market(String id) throws IOException {
        Optional<JsonNode> node = readOptional("/markets/" + encode(id));
        return node.isPresent() ? Optional.of(market(node.get())) : Optional.empty();
    }

    @Override
    public List<MarketTag> tags(int limit) throws IOException {
        return tags(read("/tags?limit=" + limit));
    }

    @Override
    public List<MarketSeries> series(int limit) throws IOException {
        List<MarketSeries> series = new ArrayList<>();
        for (JsonNode node : read("/series?limit=" + limit)) {
            series.add(new MarketSeries(required(node, "id", "series id"),
                    text(node, "ticker"), text(node, "slug"),
                    text(node, "title"), text(node, "recurrence")));
        }
        return List.copyOf(series);
    }

    @Override
    public List<Sport> sports() throws IOException {
        List<Sport> sports = new ArrayList<>();
        for (JsonNode node : read("/sports")) {
            sports.add(new Sport(required(node, "sport", "sport id"), text(node, "image"),
                    text(node, "resolution"), text(node, "ordering")));
        }
        return List.copyOf(sports);
    }

    @Override
    public SearchResults search(String query) throws IOException {
        JsonNode node = read("/public-search?q=" + encode(query));
        List<DiscoveredEvent> events = new ArrayList<>();
        for (JsonNode child : node.path("events")) {
            events.add(event(child));
        }
        return new SearchResults(events, tags(node.path("tags")));
    }

    private JsonNode read(String path) throws IOException {
        return readOptional(path).orElseThrow(
                () -> new IOException("discovery read " + path + " returned HTTP 404"));
    }

    /** A 404 is an answer here, not a failure: Gamma does not know that identifier. */
    private Optional<JsonNode> readOptional(String path) throws IOException {
        HttpOutcome outcome = runtime.get(config.gammaHost(), path, ACCEPT_JSON);
        if (outcome.status() == 404) return Optional.empty();
        if (!outcome.successful()) {
            throw new IOException("discovery read " + path + " failed with HTTP " + outcome.status());
        }
        return Optional.of(runtime.parse(outcome.body()));
    }

    private static String encode(String segment) {
        return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8);
    }

    private DiscoveredEvent event(JsonNode node) throws IOException {
        List<DiscoveredMarket> markets = new ArrayList<>();
        JsonNode nested = node.get("markets");
        if (nested != null) {
            for (JsonNode child : nested) {
                markets.add(market(child));
            }
        }
        return new DiscoveredEvent(
                required(node, "id", "event id"),
                text(node, "ticker"),
                text(node, "slug"),
                text(node, "title"),
                instant(node, "startDate"),
                instant(node, "endDate"),
                flag(node, "negRisk"),
                markets);
    }

    private DiscoveredMarket market(JsonNode node) throws IOException {
        return new DiscoveredMarket(
                required(node, "id", "market id"),
                text(node, "conditionId"),
                text(node, "slug"),
                text(node, "question"),
                outcomes(node),
                new MarketState(flag(node, "active"), flag(node, "closed"),
                        flag(node, "archived"), flag(node, "acceptingOrders"),
                        flag(node, "enableOrderBook")),
                instant(node, "startDate"),
                instant(node, "endDate"),
                new MarketPricing(decimal(node, "bestBid"), decimal(node, "bestAsk"),
                        decimal(node, "lastTradePrice"), decimal(node, "spread")),
                new MarketMetadata(decimal(node, "liquidity"), decimal(node, "volume"),
                        decimal(node, "orderMinSize"), tags(node.get("tags"))));
    }

    private static List<MarketTag> tags(JsonNode array) throws IOException {
        if (array == null || !array.isArray()) return List.of();
        List<MarketTag> tags = new ArrayList<>();
        for (JsonNode node : array) {
            tags.add(new MarketTag(required(node, "id", "tag id"),
                    text(node, "label"), text(node, "slug")));
        }
        return List.copyOf(tags);
    }

    /** Gamma publishes outcomes, prices and token ids as three parallel JSON-encoded arrays. */
    private List<MarketOutcome> outcomes(JsonNode node) {
        List<String> names = embeddedList(node, "outcomes");
        List<String> prices = embeddedList(node, "outcomePrices");
        List<String> tokenIds = embeddedList(node, "clobTokenIds");
        List<MarketOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            outcomes.add(new MarketOutcome(names.get(i),
                    at(prices, i).map(BigDecimal::new), at(tokenIds, i)));
        }
        return List.copyOf(outcomes);
    }

    private List<String> embeddedList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return List.of();
        List<String> items = new ArrayList<>();
        try {
            runtime.parse(value.asText()).forEach(item -> items.add(item.asText()));
        } catch (IOException notAnEmbeddedArray) {
            return List.of();
        }
        return items;
    }

    /** A blank parallel entry is a value Gamma did not publish, not an empty one. */
    private static Optional<String> at(List<String> values, int index) {
        return index < values.size() ? Optional.of(values.get(index)).filter(v -> !v.isBlank())
                : Optional.empty();
    }

    private static Optional<Boolean> flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? Optional.empty() : Optional.of(value.asBoolean());
    }

    /** Keeps the wire text so an exact decimal survives; a JSON number never becomes a double. */
    private static Optional<BigDecimal> decimal(JsonNode node, String field) {
        return text(node, field).map(BigDecimal::new);
    }

    private static Optional<Instant> instant(JsonNode node, String field) {
        return text(node, field).map(Instant::parse);
    }

    /** Identity is not optional: a blank or missing id would publish a market nobody can name. */
    private static String required(JsonNode node, String field, String described)
            throws IOException {
        return text(node, field).orElseThrow(
                () -> new IOException("discovery payload carried no " + described));
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? Optional.empty() : Optional.of(value.asText());
    }

    /** Builds the query string in a fixed order so a request is reproducible. */
    private static final class QueryString {
        private final StringBuilder text = new StringBuilder();

        void add(String name, String value) {
            text.append(text.isEmpty() ? '?' : '&').append(name).append('=')
                    .append(java.net.URLEncoder.encode(value,
                            java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
