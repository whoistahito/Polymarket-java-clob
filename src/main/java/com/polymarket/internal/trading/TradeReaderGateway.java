package com.polymarket.internal.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Transport for {@code GET /data/trades?id=}. One trade ID per walk; the filter has no batch
 * form, and {@code maker_address} is required — an id-only read is a documented 400.
 */
public final class TradeReaderGateway implements TradeReader {

    private static final String PATH = "/data/trades";
    // clob-openapi.yaml TradesResponse.next_cursor: "LTE=" indicates no more pages.
    private static final String LAST_PAGE = "LTE=";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;

    public TradeReaderGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public List<SettledTrade> byIds(ApiCredentials credentials, SigningIdentity identity,
            List<String> tradeIds) throws IOException {
        // POLY_ADDRESS is the Account Signer that owns the API key; maker_address is the Trading
        // Wallet named as the maker. They coincide only for an EOA.
        String accountSigner = identity.accountSigner();
        List<SettledTrade> trades = new ArrayList<>();
        for (String id : tradeIds) {
            String filter = PATH + "?id=" + encode(id)
                    + "&maker_address=" + encode(identity.tradingWallet());
            Set<String> visited = new HashSet<>();
            String cursor = null;
            while (true) {
                String path = cursor == null ? filter : filter + "&next_cursor=" + encode(cursor);
                HttpOutcome outcome = runtime.get(config.clobHost(), path,
                        l2Headers(credentials, accountSigner, path));
                if (!outcome.successful()) {
                    throw new IOException("could not read trade " + id + ": HTTP " + outcome.status());
                }
                JsonNode body = runtime.parse(outcome.body());
                body.path("data").forEach(node -> trades.add(trade(node)));

                String next = body.path("next_cursor").asText("");
                if (next.isBlank() || LAST_PAGE.equals(next) || !visited.add(next)) break;
                cursor = next;
            }
        }
        return List.copyOf(trades);
    }

    private Map<String, String> l2Headers(ApiCredentials credentials, String address, String path) {
        return L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), "GET", path, null);
    }

    private static SettledTrade trade(JsonNode node) {
        return new SettledTrade(
                node.path("id").asText(""),
                new TradeStatus(node.path("status").asText("")),
                text(node, "side").map(v -> Side.valueOf(v.toUpperCase(Locale.ROOT))),
                text(node, "asset_id"),
                text(node, "size").map(BigDecimal::new),
                text(node, "price").map(BigDecimal::new),
                instant(node.path("match_time")),
                text(node, "transaction_hash"),
                text(node, "err_msg"));
    }

    /** Absent or blank stays absent, so nothing downstream can mistake it for a real value. */
    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? Optional.empty() : Optional.of(value.asText());
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
