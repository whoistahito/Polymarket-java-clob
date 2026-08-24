package com.polymarket.internal.rfq;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.PositionId;
import com.polymarket.rfq.ComboMarket;
import com.polymarket.rfq.ComboMarketCatalog;
import com.polymarket.rfq.ComboMarketPage;
import com.polymarket.rfq.ComboMarketQuery;
import com.polymarket.rfq.ComboOutcome;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transport for the Combo markets catalog. Ground truth:
 * {@code src/test/resources/protocol/combo-markets.json}. The read is unauthenticated.
 */
public final class ComboMarketGateway implements ComboMarketCatalog {

    /** Published in the "Get Combo Markets" API tab; not one of PolymarketConfig's hosts. */
    public static final URI DEFAULT_HOST = URI.create("https://combos-rfq-api.polymarket.com");

    private static final String CATALOG_PATH = "/v1/rfq/combo-markets";
    private static final int YES_INDEX = 0;
    private static final int NO_INDEX = 1;

    private final URI host;
    private final HttpRuntime runtime;

    public ComboMarketGateway(URI host, HttpRuntime runtime) {
        this.host = host;
        this.runtime = runtime;
    }

    @Override
    public ComboMarketPage comboMarkets(ComboMarketQuery query) throws IOException {
        StringBuilder path = new StringBuilder(CATALOG_PATH)
                .append("?limit=").append(query.pageSizeValue());
        query.cursorValue().ifPresent(cursor -> path.append("&cursor=").append(encode(cursor)));
        if (!query.excluded().isEmpty()) {
            path.append("&exclude=").append(encode(String.join(",", query.excluded())));
        }

        HttpOutcome outcome = runtime.get(host, path.toString(), Map.of());
        JsonNode body = runtime.parse(outcome.body());
        if (!outcome.successful()) {
            throw new IOException("Combo catalog read failed: HTTP " + outcome.status()
                    + " " + text(body, "error").orElse(""));
        }
        List<ComboMarket> markets = new ArrayList<>();
        body.path("markets").forEach(market -> comboMarket(market).ifPresent(markets::add));
        return new ComboMarketPage(markets, text(body, "next_cursor"));
    }

    /** The documented YES/NO pair is the whole contract; anything else is skipped, never guessed. */
    private static Optional<ComboMarket> comboMarket(JsonNode node) {
        JsonNode positionIds = node.path("position_ids");
        JsonNode outcomes = node.path("outcomes");
        JsonNode prices = node.path("outcome_prices");
        if (positionIds.size() != 2 || outcomes.size() != 2) {
            return Optional.empty();
        }
        return Optional.of(new ComboMarket(
                node.path("id").asText(""),
                node.path("condition_id").asText(""),
                outcome(outcomes, positionIds, prices, YES_INDEX),
                outcome(outcomes, positionIds, prices, NO_INDEX),
                node.path("slug").asText(""),
                node.path("title").asText(""),
                text(node, "image"),
                text(node, "volume").map(BigDecimal::new),
                tags(node.path("tags"))));
    }

    private static ComboOutcome outcome(JsonNode outcomes, JsonNode positionIds, JsonNode prices,
            int index) {
        return new ComboOutcome(outcomes.path(index).asText(""),
                new PositionId(positionIds.path(index).asText("")),
                text(prices.path(index)).map(BigDecimal::new));
    }

    private static List<String> tags(JsonNode array) {
        List<String> tags = new ArrayList<>();
        array.forEach(tag -> tags.add(tag.asText()));
        return List.copyOf(tags);
    }

    /** Keeps the wire text so an exact decimal survives; a JSON number never becomes a double. */
    private static Optional<String> text(JsonNode node, String field) {
        return text(node.path(field));
    }

    private static Optional<String> text(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return Optional.empty();
        String value = node.asText("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
