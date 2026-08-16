package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.rewards.CurrentMarketRewards;
import com.polymarket.rewards.RewardConfig;
import com.polymarket.rewards.RewardCursor;
import com.polymarket.rewards.RewardPage;
import com.polymarket.rewards.RewardToken;
import com.polymarket.rewards.RewardedMarket;
import com.polymarket.rewards.Rewards;
import com.polymarket.rewards.UserEarning;
import com.polymarket.rewards.UserRewardedMarket;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rewards")
class RewardsTest {

    private static final String CONDITION_ID =
            "0xbd31dc8a20211944f6b70f31557f1001557b59905b7738480ca09bd4532f84af";
    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    private Polymarket sdk(SigningAuthority authority) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, FIXED);
    }

    private static SigningAuthority authority() {
        return SigningAuthority.signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    @Test
    @DisplayName("TC-RW-001: a multi-page market read sends every cursor in the query and terminates")
    void marketRewardsWalkEveryPageThroughTheQueryCursor() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MQ==","data":[
                  {"condition_id":"0xaaa","question":"First?","tokens":[],"rewards_config":[]}]}""");
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"LTE=","data":[
                  {"condition_id":"0xbbb","question":"Second?","tokens":[],"rewards_config":[]}]}""");

        List<RewardedMarket> markets;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            markets = sdk.rewards().allMarketRewards(CONDITION_ID);
        }

        assertEquals(List.of("0xaaa", "0xbbb"),
                markets.stream().map(RewardedMarket::conditionId).toList());
        assertEquals("/rewards/markets/" + CONDITION_ID + "?next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
        assertEquals("/rewards/markets/" + CONDITION_ID + "?next_cursor=MQ%3D%3D",
                server.takeRequest().getPath());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RW-002: the default read returns one typed page with exact decimal rewards")
    void marketRewardsReturnOneTypedPage() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MQ==","data":[{
                  "condition_id":"0xbd31","question":"Will Trump win the 2024 Iowa Caucus?",
                  "market_slug":"iowa","event_slug":"caucus","image":"https://img/1.png",
                  "rewards_max_spread":99,"rewards_min_size":10,"market_competitiveness":0.42,
                  "tokens":[{"token_id":"1343","outcome":"YES","price":0.8},
                            {"token_id":"1667","outcome":"NO","price":0.2}],
                  "rewards_config":[{"id":2,"asset_address":"0x9c4E",
                    "start_date":"2024-03-01","end_date":"2024-05-31","rate_per_day":0.0025,
                    "total_rewards":92.5,"total_days":92}]}]}""");

        RewardPage<RewardedMarket> page;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            page = sdk.rewards().marketRewards(CONDITION_ID);
        }

        assertEquals(100, page.limit());
        assertEquals(1, page.count());
        assertEquals("MQ==", page.nextCursor().orElseThrow().value());

        RewardedMarket market = page.items().get(0);
        assertEquals("0xbd31", market.conditionId());
        assertEquals("Will Trump win the 2024 Iowa Caucus?", market.question().orElseThrow());
        assertEquals("iowa", market.marketSlug().orElseThrow());
        assertEquals("caucus", market.eventSlug().orElseThrow());
        assertEquals("https://img/1.png", market.image().orElseThrow());
        assertEquals(new BigDecimal("99"), market.scoring().maxSpread().orElseThrow());
        assertEquals(new BigDecimal("10"), market.scoring().minSize().orElseThrow());
        assertEquals(new BigDecimal("0.42"), market.metrics().competitiveness().orElseThrow());

        assertEquals(List.of("YES", "NO"),
                market.tokens().stream().map(RewardToken::outcome).toList());
        assertEquals(new BigDecimal("0.8"), market.tokens().get(0).price().orElseThrow());

        RewardConfig config = market.configs().get(0);
        assertEquals(2L, config.id().orElseThrow());
        assertEquals("0x9c4E", config.assetAddress());
        assertEquals(LocalDate.of(2024, 3, 1), config.startDate());
        assertEquals(LocalDate.of(2024, 5, 31), config.endDate().orElseThrow());
        assertEquals(new BigDecimal("0.0025"), config.ratePerDay());
        assertEquals(new BigDecimal("92.5"), config.totalRewards().orElseThrow());
        assertEquals(92, config.totalDays().orElseThrow());
        assertEquals(Optional.empty(), config.remainingRewards());

        assertEquals(1, server.getRequestCount());
        assertEquals("/rewards/markets/" + CONDITION_ID + "?next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RW-003: a cursor the server did not advance ends the page walk")
    void aRepeatedCursorEndsTheWalk() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MA==","data":[
                  {"condition_id":"0xaaa","tokens":[],"rewards_config":[]}]}""");

        RewardPage<RewardedMarket> page;
        List<RewardedMarket> all;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            page = sdk.rewards().marketRewards(CONDITION_ID);
            enqueue("""
                    {"limit":100,"count":1,"next_cursor":"MA==","data":[
                      {"condition_id":"0xaaa","tokens":[],"rewards_config":[]}]}""");
            all = sdk.rewards().allMarketRewards(CONDITION_ID);
        }

        assertEquals(Optional.empty(), page.nextCursor());
        assertEquals(1, all.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RW-012: a server cycling two cursors cannot spin the all-pages walk")
    void aCursorCycleEndsTheWalk() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MQ==","data":[
                  {"condition_id":"0xaaa","tokens":[],"rewards_config":[]}]}""");
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MA==","data":[
                  {"condition_id":"0xbbb","tokens":[],"rewards_config":[]}]}""");

        List<RewardedMarket> markets;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            markets = sdk.rewards().allMarketRewards(CONDITION_ID);
        }

        assertEquals(List.of("0xaaa", "0xbbb"),
                markets.stream().map(RewardedMarket::conditionId).toList());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RW-004: current market rewards page with sponsor and native daily rates")
    void currentRewardsCarrySponsorRates() throws Exception {
        enqueue("""
                {"limit":500,"count":1,"next_cursor":"LTE=","data":[{
                  "condition_id":"0xbd31","rewards_max_spread":99,"rewards_min_size":10,
                  "rewards_config":[{"id":0,"asset_address":"0x9c4E","start_date":"2024-03-01",
                    "end_date":"2500-12-31","rate_per_day":2,"total_rewards":92}],
                  "sponsored_daily_rate":0.5,"sponsors_count":2,
                  "native_daily_rate":2.5,"total_daily_rate":3.0}]}""");

        RewardPage<CurrentMarketRewards> page;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            page = sdk.rewards().currentRewards();
        }

        assertEquals(500, page.limit());
        assertEquals(Optional.empty(), page.nextCursor());

        CurrentMarketRewards current = page.items().get(0);
        assertEquals("0xbd31", current.conditionId());
        assertEquals(new BigDecimal("99"), current.scoring().maxSpread().orElseThrow());
        assertEquals(new BigDecimal("2"), current.configs().get(0).ratePerDay());
        assertEquals(LocalDate.of(2500, 12, 31), current.configs().get(0).endDate().orElseThrow());
        assertEquals(new BigDecimal("0.5"), current.sponsoredDailyRate().orElseThrow());
        assertEquals(2, current.sponsorsCount().orElseThrow());
        assertEquals(new BigDecimal("2.5"), current.nativeDailyRate().orElseThrow());
        assertEquals(new BigDecimal("3.0"), current.totalDailyRate().orElseThrow());

        assertEquals("/rewards/markets/current?next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RW-005: rewarded markets page carries trading metrics and its next cursor")
    void rewardedMarketsCarryMetrics() throws Exception {
        enqueue("""
                {"limit":50,"count":1,"next_cursor":"NQ==","data":[{
                  "condition_id":"0xbd31","market_id":"248849","event_id":"12345",
                  "question":"Will Trump win the 2024 Iowa Caucus?","volume_24hr":12345.67,
                  "spread":0.12,"market_competitiveness":0.42,"one_day_price_change":0.03,
                  "tokens":[],"rewards_config":[]}]}""");

        RewardPage<RewardedMarket> page;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            page = sdk.rewards().rewardedMarkets(RewardCursor.of("NA=="));
        }

        RewardedMarket market = page.items().get(0);
        assertEquals("248849", market.marketId().orElseThrow());
        assertEquals("12345", market.eventId().orElseThrow());
        assertEquals(new BigDecimal("12345.67"), market.metrics().volume24hr().orElseThrow());
        assertEquals(new BigDecimal("0.12"), market.metrics().spread().orElseThrow());
        assertEquals(new BigDecimal("0.03"), market.metrics().oneDayPriceChange().orElseThrow());
        assertEquals("NQ==", page.nextCursor().orElseThrow().value());

        assertEquals("/rewards/markets/multi?next_cursor=NA%3D%3D", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RW-006: a user earnings walk carries L2 headers and signs each cursor page")
    void userEarningsAreL2AuthenticatedPerPage() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MQ==","data":[
                  {"date":"2024-03-26T00:00:00Z","condition_id":"0xbd31","asset_address":"0x9c4E",
                   "maker_address":"0xFeA4","earnings":0.237519,"asset_rate":1}]}""");
        enqueue("""
                {"limit":100,"count":0,"next_cursor":"LTE=","data":[]}""");

        RewardPage<UserEarning> first;
        RewardPage<UserEarning> second;
        try (Polymarket sdk = sdk(authority())) {
            first = sdk.rewards().earnings(LocalDate.of(2024, 3, 26));
            second = sdk.rewards().earnings(LocalDate.of(2024, 3, 26),
                    first.nextCursor().orElseThrow());
        }

        UserEarning earning = first.items().get(0);
        assertEquals(Instant.parse("2024-03-26T00:00:00Z"), earning.date());
        assertEquals("0xbd31", earning.conditionId().orElseThrow());
        assertEquals("0xFeA4", earning.makerAddress());
        assertEquals("0x9c4E", earning.amount().assetAddress());
        assertEquals(new BigDecimal("0.237519"), earning.amount().earnings());
        assertEquals(new BigDecimal("1"), earning.amount().assetRate().orElseThrow());
        assertEquals(List.of(), second.items());

        RecordedRequest page1 = server.takeRequest();
        RecordedRequest page2 = server.takeRequest();
        assertEquals("/rewards/user?date=2024-03-26&next_cursor=MA%3D%3D", page1.getPath());
        assertEquals("/rewards/user?date=2024-03-26&next_cursor=MQ%3D%3D", page2.getPath());
        assertEquals(CREDENTIALS.key(), page1.getHeader("POLY_API_KEY"));
        assertEquals(CREDENTIALS.passphrase(), page1.getHeader("POLY_PASSPHRASE"));
        assertEquals(SIGNER.address(), page1.getHeader("POLY_ADDRESS"));
        assertEquals("1773890758", page1.getHeader("POLY_TIMESTAMP"));
        // Two pages signed at the same instant differ only by the cursor, so the signed path
        // must include the query string.
        assertNotEquals(page1.getHeader("POLY_SIGNATURE"), page2.getHeader("POLY_SIGNATURE"));
    }

    @Test
    @DisplayName("TC-RW-007: a user reward read without credentials fails before anything is sent")
    void userRewardReadsRequireCredentials() throws Exception {
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            Rewards rewards = sdk.rewards();
            assertThrows(AuthenticationRequiredException.class,
                    () -> rewards.earnings(LocalDate.of(2024, 3, 26)));
            assertThrows(AuthenticationRequiredException.class,
                    () -> rewards.totalEarnings(LocalDate.of(2024, 3, 26)));
            assertThrows(AuthenticationRequiredException.class, rewards::rewardPercentages);
            assertThrows(AuthenticationRequiredException.class,
                    () -> rewards.userRewardedMarkets(LocalDate.of(2024, 3, 26)));
        }

        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RW-008: total earnings come back per asset with no condition and no cursor")
    void totalEarningsAreGroupedByAsset() throws Exception {
        enqueue("""
                [{"date":"2024-04-09T00:00:00Z","asset_address":"0x9c4E","maker_address":"0xD527",
                  "earnings":1.59984,"asset_rate":0.999357},
                 {"date":"2024-04-09T00:00:00Z","asset_address":"0x6930","maker_address":"0xD527",
                  "earnings":8.187219,"asset_rate":3.51}]""");

        List<UserEarning> totals;
        try (Polymarket sdk = sdk(authority())) {
            totals = sdk.rewards().totalEarnings(LocalDate.of(2024, 4, 9));
        }

        assertEquals(2, totals.size());
        assertEquals(Optional.empty(), totals.get(0).conditionId());
        assertEquals(new BigDecimal("1.59984"), totals.get(0).amount().earnings());
        assertEquals(new BigDecimal("0.999357"), totals.get(0).amount().assetRate().orElseThrow());
        assertEquals(new BigDecimal("8.187219"), totals.get(1).amount().earnings());

        assertEquals("/rewards/user/total?date=2024-04-09", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RW-009: reward percentages arrive as exact decimals keyed by condition")
    void rewardPercentagesAreExactDecimals() throws Exception {
        enqueue("""
                {"0x296e":20,"0xbd31":33.333333}""");

        Map<String, BigDecimal> percentages;
        try (Polymarket sdk = sdk(authority())) {
            percentages = sdk.rewards().rewardPercentages();
        }

        assertEquals(new BigDecimal("20"), percentages.get("0x296e"));
        assertEquals(new BigDecimal("33.333333"), percentages.get("0xbd31"));
        assertEquals("/rewards/user/percentages", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RW-010: a user rewarded market carries its earnings and live percentage")
    void userRewardedMarketsCarryEarnings() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"total_count":42,"next_cursor":"LTE=","data":[{
                  "condition_id":"0xbd31","market_id":"248849","question":"Iowa?",
                  "rewards_max_spread":99,"rewards_min_size":10,"tokens":[],
                  "rewards_config":[{"asset_address":"0x9c4E","start_date":"2024-03-01",
                    "rate_per_day":2}],
                  "maker_address":"0xD527","earning_percentage":30,
                  "earnings":[{"asset_address":"0x9c4E","earnings":0.585051,"asset_rate":1.001}]}]}""");

        RewardPage<UserRewardedMarket> page;
        try (Polymarket sdk = sdk(authority())) {
            page = sdk.rewards().userRewardedMarkets(LocalDate.of(2024, 3, 26));
        }

        assertEquals(42, page.totalCount().orElseThrow());
        UserRewardedMarket entry = page.items().get(0);
        assertEquals("0xbd31", entry.market().conditionId());
        assertEquals("248849", entry.market().marketId().orElseThrow());
        assertEquals(new BigDecimal("2"), entry.market().configs().get(0).ratePerDay());
        assertEquals("0xD527", entry.makerAddress().orElseThrow());
        assertEquals(new BigDecimal("30"), entry.earningPercentage().orElseThrow());
        assertEquals(new BigDecimal("0.585051"), entry.earnings().get(0).earnings());
        assertEquals(new BigDecimal("1.001"), entry.earnings().get(0).assetRate().orElseThrow());

        assertEquals("/rewards/user/markets?date=2024-03-26&next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RW-011: unknown fields are ignored and absent values never become zero")
    void unknownFieldsAreToleratedAndAbsenceIsPreserved() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"LTE=","some_new_envelope_field":true,
                 "data":[{"condition_id":"0xaaa","brand_new_field":{"nested":1},
                  "tokens":[{"token_id":"1343","outcome":"YES","another_new_field":"x"}],
                  "rewards_config":[{"asset_address":"0x9c4E","start_date":"2024-03-01",
                    "rate_per_day":2,"future_field":7}]}]}""");

        RewardedMarket market;
        try (Polymarket sdk = sdk(SigningAuthority.none())) {
            market = sdk.rewards().marketRewards(CONDITION_ID).items().get(0);
        }

        assertEquals(Optional.empty(), market.question());
        assertEquals(Optional.empty(), market.scoring().maxSpread());
        assertEquals(Optional.empty(), market.scoring().minSize());
        assertEquals(Optional.empty(), market.metrics().volume24hr());
        assertEquals(Optional.empty(), market.tokens().get(0).price());
        assertEquals(Optional.empty(), market.configs().get(0).id());
        assertEquals(Optional.empty(), market.configs().get(0).endDate());
        assertEquals(Optional.empty(), market.configs().get(0).totalRewards());
    }
}
