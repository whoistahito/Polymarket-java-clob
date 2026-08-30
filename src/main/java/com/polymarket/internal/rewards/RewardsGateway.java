package com.polymarket.internal.rewards;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.rewards.AssetEarning;
import com.polymarket.rewards.CurrentMarketRewards;
import com.polymarket.rewards.MarketMetrics;
import com.polymarket.rewards.RewardConfig;
import com.polymarket.rewards.RewardCursor;
import com.polymarket.rewards.RewardLedger;
import com.polymarket.rewards.RewardPage;
import com.polymarket.rewards.RewardToken;
import com.polymarket.rewards.RewardedMarket;
import com.polymarket.rewards.ScoringRules;
import com.polymarket.rewards.UserEarning;
import com.polymarket.rewards.UserRewardedMarket;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** CLOB transport for reward reads; maps wire JSON to domain values and nothing else. */
public final class RewardsGateway implements RewardLedger {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;

    public RewardsGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public RewardPage<RewardedMarket> marketRewards(String conditionId, RewardCursor cursor)
            throws IOException {
        return page("/rewards/markets/" + encode(conditionId) + query("", cursor), cursor,
                ACCEPT_JSON, RewardsGateway::rewardedMarket);
    }

    @Override
    public RewardPage<CurrentMarketRewards> currentRewards(RewardCursor cursor) throws IOException {
        return page("/rewards/markets/current" + query("", cursor), cursor, ACCEPT_JSON,
                RewardsGateway::currentMarketRewards);
    }

    @Override
    public RewardPage<RewardedMarket> rewardedMarkets(RewardCursor cursor) throws IOException {
        return page("/rewards/markets/multi" + query("", cursor), cursor, ACCEPT_JSON,
                RewardsGateway::rewardedMarket);
    }

    @Override
    public RewardPage<UserEarning> earnings(ApiCredentials credentials, String address,
            LocalDate date, RewardCursor cursor) throws IOException {
        return authenticatedPage("/rewards/user", "date=" + date, cursor, credentials, address,
                node -> userEarning(node, text(node, "condition_id")));
    }

    @Override
    public List<UserEarning> totalEarnings(ApiCredentials credentials, String address,
            LocalDate date) throws IOException {
        JsonNode body = authenticatedRead(
                "/rewards/user/total?date=" + date, credentials, address);
        List<UserEarning> totals = new ArrayList<>();
        body.forEach(node -> totals.add(userEarning(node, Optional.empty())));
        return List.copyOf(totals);
    }

    @Override
    public Map<String, BigDecimal> rewardPercentages(ApiCredentials credentials, String address)
            throws IOException {
        JsonNode body = authenticatedRead("/rewards/user/percentages", credentials, address);
        Map<String, BigDecimal> percentages = new LinkedHashMap<>();
        body.fields().forEachRemaining(
                entry -> percentages.put(entry.getKey(), new BigDecimal(entry.getValue().asText())));
        return Map.copyOf(percentages);
    }

    @Override
    public RewardPage<UserRewardedMarket> userRewardedMarkets(ApiCredentials credentials,
            String address, LocalDate date, RewardCursor cursor) throws IOException {
        return authenticatedPage("/rewards/user/markets", "date=" + date, cursor, credentials,
                address, RewardsGateway::userRewardedMarket);
    }

    /** The L2 signature covers the path AND its query, so the cursor is part of what is signed. */
    private <T> RewardPage<T> authenticatedPage(String path, String filters, RewardCursor cursor,
            ApiCredentials credentials, String address, Function<JsonNode, T> mapper)
            throws IOException {
        String pathWithQuery = path + query(filters, cursor);
        return page(pathWithQuery, cursor,
                l2Headers(credentials, address, pathWithQuery), mapper);
    }

    private JsonNode authenticatedRead(String pathWithQuery, ApiCredentials credentials,
            String address) throws IOException {
        return body(pathWithQuery,
                runtime.get(config.clobHost(), pathWithQuery,
                        l2Headers(credentials, address, pathWithQuery)));
    }

    private <T> RewardPage<T> page(String pathWithQuery, RewardCursor cursor,
            Map<String, String> headers, Function<JsonNode, T> mapper) throws IOException {
        JsonNode body = body(pathWithQuery,
                runtime.get(config.clobHost(), pathWithQuery, headers));
        List<T> items = new ArrayList<>();
        body.path("data").forEach(node -> items.add(mapper.apply(node)));
        return new RewardPage<>(items,
                RewardCursor.next(cursor, text(body, "next_cursor").orElse(null)),
                body.path("limit").asInt(), body.path("count").asInt(),
                integral(body, "total_count").map(Long::intValue));
    }

    /** The cursor always travels in the documented {@code next_cursor} query parameter. */
    private static String query(String filters, RewardCursor cursor) {
        return "?" + (filters.isEmpty() ? "" : filters + "&")
                + "next_cursor=" + encode(cursor.value());
    }

    private JsonNode body(String path, HttpOutcome outcome) throws IOException {
        if (!outcome.successful()) {
            throw new IOException("reward read " + path + " failed with HTTP " + outcome.status());
        }
        return runtime.parse(outcome.body());
    }

    private Map<String, String> l2Headers(ApiCredentials credentials, String address,
            String signedPath) {
        return L2Attestation.headers(credentials, address, clock.instant().getEpochSecond(),
                "GET", signedPath, null);
    }

    private static RewardedMarket rewardedMarket(JsonNode node) {
        return new RewardedMarket(
                node.path("condition_id").asText(),
                text(node, "market_id"),
                text(node, "event_id"),
                text(node, "question"),
                text(node, "market_slug"),
                text(node, "event_slug"),
                text(node, "image"),
                scoring(node),
                metrics(node),
                tokens(node.get("tokens")),
                configs(node.get("rewards_config")));
    }

    private static CurrentMarketRewards currentMarketRewards(JsonNode node) {
        return new CurrentMarketRewards(
                node.path("condition_id").asText(),
                scoring(node),
                configs(node.get("rewards_config")),
                decimal(node, "sponsored_daily_rate"),
                integral(node, "sponsors_count").map(Long::intValue),
                decimal(node, "native_daily_rate"),
                decimal(node, "total_daily_rate"));
    }

    private static UserEarning userEarning(JsonNode node, Optional<String> conditionId) {
        return new UserEarning(Instant.parse(node.path("date").asText()), conditionId,
                node.path("maker_address").asText(), assetEarning(node));
    }

    private static UserRewardedMarket userRewardedMarket(JsonNode node) {
        List<AssetEarning> earnings = new ArrayList<>();
        JsonNode array = node.get("earnings");
        if (array != null) array.forEach(child -> earnings.add(assetEarning(child)));
        return new UserRewardedMarket(rewardedMarket(node), text(node, "maker_address"),
                decimal(node, "earning_percentage"), earnings);
    }

    private static AssetEarning assetEarning(JsonNode node) {
        return new AssetEarning(node.path("asset_address").asText(),
                decimal(node, "earnings").orElseThrow(
                        () -> new IllegalStateException("earning has no earnings amount")),
                decimal(node, "asset_rate"));
    }

    private static ScoringRules scoring(JsonNode node) {
        return new ScoringRules(decimal(node, "rewards_max_spread"),
                decimal(node, "rewards_min_size"));
    }

    private static MarketMetrics metrics(JsonNode node) {
        return new MarketMetrics(decimal(node, "volume_24hr"), decimal(node, "spread"),
                decimal(node, "market_competitiveness"), decimal(node, "one_day_price_change"));
    }

    private static List<RewardToken> tokens(JsonNode array) {
        List<RewardToken> tokens = new ArrayList<>();
        if (array != null) {
            array.forEach(node -> tokens.add(new RewardToken(node.path("token_id").asText(),
                    node.path("outcome").asText(), decimal(node, "price"))));
        }
        return List.copyOf(tokens);
    }

    private static List<RewardConfig> configs(JsonNode array) {
        List<RewardConfig> configs = new ArrayList<>();
        if (array != null) {
            array.forEach(node -> configs.add(new RewardConfig(
                    integral(node, "id"),
                    node.path("asset_address").asText(),
                    LocalDate.parse(node.path("start_date").asText()),
                    text(node, "end_date").map(LocalDate::parse),
                    decimal(node, "rate_per_day").orElseThrow(
                            () -> new IllegalStateException("reward config has no rate_per_day")),
                    decimal(node, "total_rewards"),
                    decimal(node, "remaining_reward_amount"),
                    integral(node, "total_days").map(Long::intValue))));
        }
        return List.copyOf(configs);
    }

    // Percent-encoded like the Rust reference client, because the same text is L2-signed.
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Keeps the wire text so an exact decimal survives; a JSON number never becomes a double. */
    private static Optional<BigDecimal> decimal(JsonNode node, String field) {
        return text(node, field).map(BigDecimal::new);
    }

    private static Optional<Long> integral(JsonNode node, String field) {
        return text(node, field).map(Long::parseLong);
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? Optional.empty() : Optional.of(value.asText());
    }
}
