package com.polymarket.internal.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.trading.SettledTrade;
import com.polymarket.trading.Side;
import com.polymarket.trading.TradeReader;
import com.polymarket.trading.TradeStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Transport for {@code GET /data/trades?id=}. One trade ID per request; the filter has no batch form. */
public final class TradeReaderGateway implements TradeReader {

    private static final String PATH = "/data/trades";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;

    public TradeReaderGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public List<SettledTrade> byIds(ApiCredentials credentials, String address, List<String> tradeIds)
            throws IOException {
        List<SettledTrade> trades = new ArrayList<>();
        for (String id : tradeIds) {
            String path = PATH + "?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
            HttpOutcome outcome = runtime.get(config.clobHost(), path, l2Headers(credentials, address, path));
            if (!outcome.successful()) {
                throw new IOException("could not read trade " + id + ": HTTP " + outcome.status());
            }
            runtime.parse(outcome.body()).forEach(node -> trades.add(trade(node)));
        }
        return List.copyOf(trades);
    }

    private Map<String, String> l2Headers(ApiCredentials credentials, String address, String path) {
        return L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), "GET", path, null);
    }

    private static SettledTrade trade(JsonNode node) {
        return new SettledTrade(
                node.path("id").asText(),
                new TradeStatus(node.path("status").asText("")),
                Side.valueOf(node.path("side").asText("BUY").toUpperCase(Locale.ROOT)),
                node.path("asset_id").asText(),
                new BigDecimal(node.path("size").asText("0")),
                new BigDecimal(node.path("price").asText("0")),
                instant(node.path("match_time")),
                blankToEmpty(node.path("transaction_hash").asText(null)));
    }

    private static Optional<Instant> instant(JsonNode node) {
        String text = node.asText(null);
        if (text == null || text.isBlank()) return Optional.empty();
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(text)));
        } catch (NumberFormatException notEpoch) {
            try {
                return Optional.of(Instant.parse(text));
            } catch (DateTimeParseException notIso) {
                return Optional.empty();
            }
        }
    }

    private static Optional<String> blankToEmpty(String s) {
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}
