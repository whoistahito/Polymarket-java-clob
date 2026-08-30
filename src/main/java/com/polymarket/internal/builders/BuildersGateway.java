package com.polymarket.internal.builders;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.builders.BuilderCredentialRevocation;
import com.polymarket.builders.BuilderCredentialSummary;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.builders.BuilderCursor;
import com.polymarket.builders.BuilderDirectory;
import com.polymarket.builders.BuilderTrade;
import com.polymarket.builders.BuilderTradePage;
import com.polymarket.builders.BuilderTradeQuery;
import com.polymarket.builders.Side;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Transport and protocol for the builder credential lifecycle and builder trade reads. */
public final class BuildersGateway implements BuilderDirectory {

    private static final String CREDENTIAL_PATH = "/auth/builder-api-key";
    private static final String TRADES_PATH = "/builder/trades";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;

    public BuildersGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public BuilderCredentials create(ApiCredentials credentials, String address) throws IOException {
        String body = "{}";
        HttpOutcome outcome = runtime.post(config.clobHost(), CREDENTIAL_PATH,
                l2Headers(credentials, address, "POST", CREDENTIAL_PATH, body), body);
        if (!outcome.successful()) {
            throw new IOException(
                    "could not create a builder API key: HTTP " + outcome.status());
        }
        JsonNode node = runtime.parse(outcome.body());
        return new BuilderCredentials(
                node.path("key").asText(), node.path("secret").asText(),
                node.path("passphrase").asText());
    }

    @Override
    public List<BuilderCredentialSummary> list(ApiCredentials credentials, String address)
            throws IOException {
        HttpOutcome outcome = runtime.get(config.clobHost(), CREDENTIAL_PATH,
                l2Headers(credentials, address, "GET", CREDENTIAL_PATH, null));
        if (!outcome.successful()) {
            throw new IOException("could not list builder API keys: HTTP " + outcome.status());
        }
        List<BuilderCredentialSummary> summaries = new ArrayList<>();
        runtime.parse(outcome.body()).forEach(node -> summaries.add(new BuilderCredentialSummary(
                node.path("key").asText(), instant(node, "createdAt"), instant(node, "revokedAt"))));
        return List.copyOf(summaries);
    }

    @Override
    public BuilderCredentialRevocation revoke(ApiCredentials credentials, String address)
            throws IOException {
        HttpOutcome outcome = runtime.delete(config.clobHost(), CREDENTIAL_PATH,
                l2Headers(credentials, address, "DELETE", CREDENTIAL_PATH, null));
        return outcome.successful()
                ? BuilderCredentialRevocation.succeeded()
                : BuilderCredentialRevocation.failed("HTTP " + outcome.status());
    }

    @Override
    public BuilderTradePage trades(ApiCredentials credentials, String address,
            BuilderTradeQuery query, BuilderCursor cursor) throws IOException {
        String pathWithQuery = TRADES_PATH + query(query, cursor);
        HttpOutcome outcome = runtime.get(config.clobHost(), pathWithQuery,
                l2Headers(credentials, address, "GET", pathWithQuery, null));
        if (!outcome.successful()) {
            throw new IOException("could not read builder trades: HTTP " + outcome.status());
        }
        JsonNode body = runtime.parse(outcome.body());
        List<BuilderTrade> trades = new ArrayList<>();
        for (JsonNode node : body.path("data")) {
            trades.add(trade(node));
        }
        return new BuilderTradePage(trades,
                BuilderCursor.next(cursor, text(body, "next_cursor").orElse(null)),
                body.path("limit").asInt(), body.path("count").asInt());
    }

    /** The L2 signature covers the path AND its query, so the cursor and filters are signed too. */
    private static String query(BuilderTradeQuery filter, BuilderCursor cursor) {
        StringBuilder sb = new StringBuilder("?builder_code=");
        sb.append(encode(filter.builderCode())).append('&');
        filter.id().ifPresent(v -> sb.append("id=").append(encode(v)).append('&'));
        filter.market().ifPresent(v -> sb.append("market=").append(encode(v)).append('&'));
        filter.assetId().ifPresent(v -> sb.append("asset_id=").append(encode(v)).append('&'));
        filter.before().ifPresent(v -> sb.append("before=").append(v.getEpochSecond()).append('&'));
        filter.after().ifPresent(v -> sb.append("after=").append(v.getEpochSecond()).append('&'));
        sb.append("next_cursor=").append(encode(cursor.value()));
        return sb.toString();
    }

    private Map<String, String> l2Headers(ApiCredentials credentials, String address,
            String method, String path, String body) {
        return L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), method, path, body);
    }

    private static BuilderTrade trade(JsonNode node) throws IOException {
        return new BuilderTrade(
                required(node, "id"),
                required(node, "tradeType"),
                required(node, "takerOrderHash"),
                required(node, "builder"),
                required(node, "market"),
                required(node, "assetId"),
                Side.valueOf(required(node, "side").toUpperCase(Locale.ROOT)),
                requiredDecimal(node, "size"),
                requiredDecimal(node, "sizeUsdc"),
                requiredDecimal(node, "price"),
                required(node, "status"),
                required(node, "outcome"),
                requiredInteger(node, "outcomeIndex"),
                required(node, "owner"),
                required(node, "maker"),
                required(node, "transactionHash"),
                unixSeconds(node, "matchTime"),
                requiredInteger(node, "bucketIndex"),
                requiredDecimal(node, "fee"),
                requiredDecimal(node, "feeUsdc"),
                text(node, "err_msg"),
                instant(node, "createdAt"),
                instant(node, "updatedAt"));
    }

    /** A required field is not optional: a dropped one would silently rewrite the trade. */
    private static String required(JsonNode node, String field) throws IOException {
        return text(node, field).orElseThrow(
                () -> new IOException("builder trade carried no " + field));
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) throws IOException {
        return new BigDecimal(required(node, field));
    }

    private static int requiredInteger(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IOException("builder trade carried no " + field);
        }
        return value.asInt();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Optional<Instant> instant(JsonNode node, String field) {
        return text(node, field).map(Instant::parse);
    }

    /** matchTime is unix seconds as a decimal string; createdAt/updatedAt are ISO-8601. */
    private static Instant unixSeconds(JsonNode node, String field) throws IOException {
        try {
            return Instant.ofEpochSecond(Long.parseLong(required(node, field)));
        } catch (NumberFormatException e) {
            throw new IOException("builder trade " + field + " is not unix seconds", e);
        }
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? Optional.empty() : Optional.of(value.asText());
    }
}
