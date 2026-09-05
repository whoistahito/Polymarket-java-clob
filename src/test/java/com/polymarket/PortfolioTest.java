package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.portfolio.ActivityKind;
import com.polymarket.portfolio.ActivityQuery;
import com.polymarket.portfolio.ActivityRecord;
import com.polymarket.portfolio.AssetType;
import com.polymarket.portfolio.BalanceSnapshot;
import com.polymarket.portfolio.ComboPositionQuery;
import com.polymarket.portfolio.ComboPositionSnapshot;
import com.polymarket.portfolio.ComboStatus;
import com.polymarket.portfolio.Notification;
import com.polymarket.portfolio.NotificationKind;
import com.polymarket.portfolio.OpenOrder;
import com.polymarket.portfolio.OpenOrderPage;
import com.polymarket.portfolio.OpenOrderQuery;
import com.polymarket.portfolio.OrderLifetime;
import com.polymarket.portfolio.OrderStatus;
import com.polymarket.portfolio.PageCursor;
import com.polymarket.portfolio.PortfolioPage;
import com.polymarket.portfolio.PositionQuery;
import com.polymarket.portfolio.PositionSnapshot;
import com.polymarket.portfolio.Side;
import com.polymarket.portfolio.TradeQuery;
import com.polymarket.portfolio.TradeRecord;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioTest {

    private static final String USER = "0x56687bf447db6ffa42ffe2208a51ce8ba1a5b63a";
    private static final String CONDITION_A =
            "0x00000000000000000000000000000000000000000000000000000000000000aa";
    private static final String CONDITION_B =
            "0x00000000000000000000000000000000000000000000000000000000000000bb";
    private static final String COMBO_CONDITION =
            "0x0391ab0ebea17b65ba87e071b0566e816b0000000000000000000000000000";
    private static final String TEST_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String SIGNER_ADDRESS = PrivateKeySigner.of(TEST_KEY).address();
    private static final Clock OBSERVED_AT =
            Clock.fixed(Instant.parse("2026-08-16T09:30:00Z"), ZoneOffset.UTC);
    private static final Clock SIGNED_AT =
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

    private Polymarket sdk() {
        return sdk(SigningAuthority.none(), OBSERVED_AT);
    }

    private Polymarket sdk(SigningAuthority authority, Clock clock) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, clock);
    }

    private static SigningAuthority authorityWithCredentials() {
        PrivateKeySigner signer = PrivateKeySigner.of(TEST_KEY);
        return SigningAuthority.signing(signer, SigningIdentity.eoa(signer.address()))
                .withApiCredentials(new ApiCredentials("f4f247b7-4ac7-ff29-a152-04fda0a8755a",
                        "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase"));
    }

    /** Body shaped from the official Data API {@code Position} schema, read on 2026-08-16. */
    private void enqueueFixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/data/" + name)) {
            server.enqueue(new MockResponse().setBody(new String(in.readAllBytes(),
                    StandardCharsets.UTF_8)));
        }
    }

    @Test
    void shouldMapPositionsToSnapshotsWhenPageIsRead() throws Exception {
        enqueueFixture("positions.json");

        PortfolioPage<PositionSnapshot> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().positions(PositionQuery.forUser(USER));
        }

        PositionSnapshot held = page.items().get(0);
        assertEquals("1343197538147866997676250008839231694243646439454152539053893078719042421992",
                held.asset());
        assertEquals("0xbd31dc8a20211944f6b70f31557f1001557b59905b7738480ca09bd4532f84af",
                held.conditionId());
        assertEquals(new BigDecimal("1500.25"), held.size());
        assertEquals(Instant.parse("2026-08-16T09:30:00Z"), held.observedAt());
        assertEquals(new BigDecimal("0.4523"), held.valuation().averagePrice().orElseThrow());
        assertEquals(new BigDecimal("680.163075"),
                held.valuation().grossInitialValue().orElseThrow());
        assertEquals(new BigDecimal("1.5"), held.valuation().entryFeesUsdc().orElseThrow());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/positions?user=" + USER + "&limit=100&offset=0", request.getPath());
    }

    @Test
    void shouldPreserveZeroSizeWhenPositionIsSoldOut() throws Exception {
        enqueueFixture("positions.json");

        PortfolioPage<PositionSnapshot> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().positions(PositionQuery.forUser(USER));
        }

        PositionSnapshot soldOut = page.items().get(1);
        assertEquals(0, soldOut.size().signum());
        assertEquals(Optional.empty(), soldOut.valuation().cashPnl());
        assertEquals(Optional.empty(), soldOut.redeemable());
        assertEquals(Optional.of(""), soldOut.market().outcome());
        assertTrue(page.complete());
        assertEquals(Optional.empty(), page.nextCursor());
    }

    @Test
    void shouldCarryNextCursorWhenPageIsFull() throws Exception {
        enqueueFixture("positions.json");
        enqueueFixture("positions.json");

        try (Polymarket sdk = sdk()) {
            PortfolioPage<PositionSnapshot> first =
                    sdk.portfolio().positions(PositionQuery.forUser(USER), PageCursor.firstPage(2));

            assertFalse(first.complete());
            assertEquals(new PageCursor(2, 2), first.nextCursor().orElseThrow());

            sdk.portfolio().positions(PositionQuery.forUser(USER), first.nextCursor().orElseThrow());
        }

        assertEquals("/positions?user=" + USER + "&limit=2&offset=0", server.takeRequest().getPath());
        assertEquals("/positions?user=" + USER + "&limit=2&offset=2", server.takeRequest().getPath());
    }

    @Test
    void shouldReportExhaustedOffsetWhenPageBudgetIsSpent() throws Exception {
        enqueueFixture("positions.json");

        PortfolioPage<PositionSnapshot> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().positions(PositionQuery.forUser(USER), new PageCursor(10_000, 2));
        }

        assertEquals(Optional.empty(), page.nextCursor());
        assertFalse(page.complete());
    }

    @Test
    void shouldThrowWhenPositionPageExceedsLimit() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .positions(PositionQuery.forUser(USER), PageCursor.firstPage(501)));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldSendPositionFiltersWhenQueryIsRead() throws Exception {
        enqueueFixture("positions.json");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().positions(PositionQuery.forUser(USER)
                    .markets(List.of(CONDITION_A, CONDITION_B))
                    .sizeThreshold(new BigDecimal("0.5"))
                    .redeemable(true)
                    .mergeable(false)
                    .includeArchived(true));
        }

        assertEquals("/positions?user=" + USER + "&market=" + CONDITION_A + "%2C" + CONDITION_B
                + "&sizeThreshold=0.5"
                + "&redeemable=true&mergeable=false&includeArchived=true&limit=100&offset=0",
                server.takeRequest().getPath());
    }

    @Test
    void shouldMapTradesToTypedRowsWhenPageIsRead() throws Exception {
        enqueueFixture("trades.json");

        PortfolioPage<TradeRecord> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().trades(TradeQuery.create().user(USER));
        }

        TradeRecord bought = page.items().get(0);
        assertEquals("0xbd31dc8a20211944f6b70f31557f1001557b59905b7738480ca09bd4532f84af",
                bought.conditionId());
        assertEquals(new BigDecimal("250.75"), bought.size());
        assertEquals(new BigDecimal("0.4523"), bought.price());
        assertEquals(Instant.parse("2026-04-15T11:00:00Z"), bought.executedAt());
        assertTrue(bought.side().isBuy());
        assertEquals(Optional.of(Side.BUY), bought.side().known());
        assertEquals("0x9f2f5e0f4a1f7c5a2d6b8e0c1a3d5f7b9c1e3a5d7f9b1c3e5a7d9f1b3c5e7a9d",
                bought.transactionHash().orElseThrow());
        assertEquals("Yes", bought.market().outcome().orElseThrow());

        TradeRecord sold = page.items().get(1);
        assertTrue(sold.side().isSell());
        assertEquals(Optional.empty(), sold.proxyWallet());
        assertEquals(Optional.empty(), sold.transactionHash());
        assertEquals(Optional.empty(), sold.market().title());

        assertEquals("/trades?user=" + USER + "&limit=100&offset=0",
                server.takeRequest().getPath());
    }

    @Test
    void shouldSendTradeFiltersWhenQueryIsRead() throws Exception {
        enqueueFixture("trades.json");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().trades(TradeQuery.create()
                    .user(USER)
                    .markets(List.of(CONDITION_A))
                    .side(Side.SELL)
                    .takerOnly(false)
                    .from(Instant.ofEpochSecond(1))
                    .to(Instant.ofEpochSecond(1776250800L)), PageCursor.firstPage(500));

            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .trades(TradeQuery.create().user(USER), new PageCursor(10_001, 100)));
        }

        assertEquals("/trades?user=" + USER + "&market=" + CONDITION_A + "&side=SELL&takerOnly=false"
                        + "&start=1&end=1776250800&limit=500&offset=0",
                server.takeRequest().getPath());
        assertEquals(1, server.getRequestCount());
    }

    /** Envelope copied from clob-openapi.yaml GET /data/orders, example "User orders response". */
    @Test
    void shouldMapOpenOrdersWhenAuthenticatedPageIsRead() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"limit":100,"next_cursor":"MTAw","count":2,"data":[
                  {"id":"0xabcdef1234567890abcdef1234567890abcdef12","status":"ORDER_STATUS_LIVE",
                   "owner":"f4f247b7-4ac7-ff29-a152-04fda0a8755a",
                   "maker_address":"0x1234567890123456789012345678901234567890",
                   "market":"0x0000000000000000000000000000000000000000000000000000000000000001",
                   "asset_id":"0xabc123def456","side":"BUY","original_size":"100000000",
                   "size_matched":"0","price":"0.5","outcome":"YES","expiration":"1735689600",
                   "order_type":"GTC","associate_trades":[],"created_at":1700000000},
                  {"id":"0xfedcba0987654321fedcba0987654321fedcba09","status":"ORDER_STATUS_LIVE",
                   "owner":"f4f247b7-4ac7-ff29-a152-04fda0a8755a",
                   "maker_address":"0x1234567890123456789012345678901234567890",
                   "market":"0x0000000000000000000000000000000000000000000000000000000000000002",
                   "asset_id":"0xdef456abc789","side":"SELL","original_size":"200000000",
                   "size_matched":"50000000","price":"0.75","outcome":"NO","expiration":"0",
                   "order_type":"GTC","associate_trades":["trade-123"],"created_at":1700000001}]}"""));

        OpenOrderPage page;
        try (Polymarket sdk = sdk(authorityWithCredentials(), SIGNED_AT)) {
            page = sdk.portfolio().openOrders(OpenOrderQuery.create());
        }

        OpenOrder resting = page.items().get(0);
        assertEquals("0xabcdef1234567890abcdef1234567890abcdef12", resting.id());
        assertTrue(resting.status().is(OrderStatus.Known.LIVE));
        assertTrue(resting.side().isBuy());
        assertEquals(new BigDecimal("100000000"), resting.originalSize());
        assertEquals(new BigDecimal("0"), resting.sizeMatched());
        assertEquals(new BigDecimal("0.5"), resting.price());
        assertTrue(resting.orderType().is(OrderLifetime.Known.GTC));
        assertEquals(Instant.ofEpochSecond(1735689600L), resting.expiresAt().orElseThrow());
        assertEquals(Instant.ofEpochSecond(1700000000L), resting.createdAt());
        assertEquals(List.of(), resting.associatedTradeIds());

        OpenOrder resting2 = page.items().get(1);
        assertEquals(Optional.empty(), resting2.expiresAt());
        assertEquals(List.of("trade-123"), resting2.associatedTradeIds());

        assertEquals(100, page.limit());
        assertEquals(2, page.count());
        assertEquals("MTAw", page.nextCursor().orElseThrow().value());
        assertEquals(1, server.getRequestCount());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/data/orders?next_cursor=MA%3D%3D", request.getPath());
        assertEquals(SIGNER_ADDRESS, request.getHeader("POLY_ADDRESS"));
        // HMAC over "1773890758GET/data/orders?next_cursor=MA%3D%3D", computed with Python hmac.
        assertEquals("s7WuFj07EpbI1IhmGHFSrm2DuCnm9DnYmjHE7CRBds8=",
                request.getHeader("POLY_SIGNATURE"));
    }

    /** Body shaped from data-openapi.yaml CombosResponse/ComboPosition, read 2026-08-23. */
    @Test
    void shouldMapComboPositionsWhenPageIsRead() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"combos":[{"combo_condition_id":"%s","combo_position_id":"77120044",
                  "module_id":3,"user_address":"%s","shares_balance":"9000.000000",
                  "entry_avg_price_usdc":"0.999999","entry_cost_usdc":"8999.991000",
                  "realized_payout_usdc":"0.00","total_cost_usdc":"8999.991000",
                  "gross_entry_cost_usdc":"8999.997488","entry_fees_usdc":"0.006488",
                  "status":"OPEN","first_entry_at":"2026-08-01T10:00:00Z","resolved_at":null,
                  "updated_at":"2026-08-16T09:00:00Z","legs_total":2,"legs_resolved":0,
                  "legs_pending":2,"legs":[
                    {"leg_index":0,"leg_position_id":"555","leg_condition_id":"%s",
                     "leg_outcome_index":0,"leg_outcome_label":"Yes","leg_status":"OPEN",
                     "leg_resolved_at":null,"leg_current_price":"0.62"},
                    {"leg_index":1,"leg_position_id":"666","leg_condition_id":"%s",
                     "leg_outcome_index":1,"leg_outcome_label":"No","leg_status":"OPEN",
                     "leg_resolved_at":null,"leg_current_price":"0"}]}],
                 "pagination":{"limit":20,"offset":0,"has_more":false,"next_cursor":null}}"""
                .formatted(COMBO_CONDITION, USER, CONDITION_A, CONDITION_B)));

        PortfolioPage<ComboPositionSnapshot> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().comboPositions(ComboPositionQuery.forUser(USER));
        }

        ComboPositionSnapshot combo = page.items().get(0);
        assertEquals(COMBO_CONDITION, combo.comboConditionId());
        assertEquals("77120044", combo.comboPositionId());
        assertEquals(new BigDecimal("9000.000000"), combo.sharesBalance());
        assertTrue(combo.status().is(ComboStatus.Known.OPEN));
        assertEquals(new BigDecimal("8999.997488"), combo.grossEntryCostUsdc().orElseThrow());
        assertEquals(new BigDecimal("0.006488"), combo.entryFeesUsdc().orElseThrow());
        assertEquals(Optional.empty(), combo.resolvedAt());
        assertEquals(Instant.parse("2026-08-16T09:00:00Z"), combo.updatedAt().orElseThrow());
        assertEquals(Instant.parse("2026-08-16T09:30:00Z"), combo.observedAt());

        assertEquals(2, combo.legs().size());
        assertEquals("555", combo.legs().get(0).positionId());
        assertEquals(Optional.of("Yes"), combo.legs().get(0).outcomeLabel());
        assertEquals(new BigDecimal("0.62"), combo.legs().get(0).currentPrice().orElseThrow());
        assertTrue(combo.legs().get(1).status().is(ComboStatus.Known.OPEN));

        assertTrue(page.complete());
        assertEquals(Optional.empty(), page.nextCursor());
        assertEquals("/v1/positions/combos?user=" + USER + "&limit=100&offset=0",
                server.takeRequest().getPath());
    }

    @Test
    void shouldValidateComboBoundariesWhenQueryIsBuilt() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"combos":[],"pagination":{"limit":20,"offset":0,"has_more":false}}"""));

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().comboPositions(ComboPositionQuery.forUser(USER)
                    .statuses(List.of(ComboStatus.Known.RESOLVED_WIN, ComboStatus.Known.PARTIAL))
                    .combos(List.of(COMBO_CONDITION)));

            // data-openapi.yaml ComboConditionId: ^0x[a-fA-F0-9]{62}$ — a 64-hex hash is not one.
            assertThrows(IllegalArgumentException.class,
                    () -> ComboPositionQuery.forUser(USER).combos(List.of(CONDITION_A)));
            // data-openapi.yaml GET /v1/positions/combos: limit <= 1000, offset <= 100000.
            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .comboPositions(ComboPositionQuery.forUser(USER), PageCursor.firstPage(1001)));
        }

        assertEquals("/v1/positions/combos?user=" + USER
                + "&status=RESOLVED_WIN%2CPARTIAL&market_id=" + COMBO_CONDITION
                + "&limit=100&offset=0", server.takeRequest().getPath());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldMapBalancesExactlyWhenAuthenticatedReadSucceeds() throws Exception {
        // clob-openapi.yaml BalanceAllowanceResponse: fixed-math with 6 decimals (pUSD).
        server.enqueue(new MockResponse().setBody("""
                {"balance":"1234567",
                 "allowances":{"0xE111180000d2663C0091e4f400237545B87B996B":"500000000"}}"""));

        BalanceSnapshot snapshot;
        try (Polymarket sdk = sdk(authorityWithCredentials(), SIGNED_AT)) {
            snapshot = sdk.portfolio().collateralBalance();
        }

        assertEquals(AssetType.COLLATERAL, snapshot.assetType());
        assertEquals(Optional.empty(), snapshot.tokenId());
        assertEquals(new BigDecimal("1.234567"), snapshot.balance());
        assertEquals(new BigDecimal("500.000000"),
                snapshot.allowances().get("0xE111180000d2663C0091e4f400237545B87B996B"));
        assertEquals(Instant.ofEpochSecond(1773890758L), snapshot.observedAt());

        RecordedRequest request = server.takeRequest();
        assertEquals("/balance-allowance?asset_type=COLLATERAL&signature_type=0",
                request.getPath());
        // HMAC over "1773890758GET/balance-allowance?asset_type=COLLATERAL&signature_type=0".
        assertEquals("d07Ak13tTAUgRmKapbWKER8LRDt6ktsm-idn2_uQ5lA=",
                request.getHeader("POLY_SIGNATURE"));
    }

    @Test
    void shouldMapConditionalBalanceWhenAuthenticatedReadSucceeds() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"balance":"0","allowances":{}}"""));

        BalanceSnapshot snapshot;
        try (Polymarket sdk = sdk(authorityWithCredentials(), SIGNED_AT)) {
            snapshot = sdk.portfolio().conditionalBalance("123456789");
        }

        assertEquals(AssetType.CONDITIONAL, snapshot.assetType());
        assertEquals(Optional.of("123456789"), snapshot.tokenId());
        assertEquals(new BigDecimal("0.000000"), snapshot.balance());
        assertEquals(Map.of(), snapshot.allowances());
        assertEquals("/balance-allowance?asset_type=CONDITIONAL&token_id=123456789"
                + "&signature_type=0", server.takeRequest().getPath());

        try (Polymarket sdk = sdk()) {
            assertThrows(AuthenticationRequiredException.class,
                    () -> sdk.portfolio().collateralBalance());
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenOpenOrdersReadLacksCredentials() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(AuthenticationRequiredException.class,
                    () -> sdk.portfolio().openOrders(OpenOrderQuery.create()));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldPreserveZeroWhenLaterPositionSnapshotCloses() throws Exception {
        String asset = "1343197538147866997676250008839231694243646439454152539053893078719042421992";
        server.enqueue(new MockResponse().setBody("""
                [{"asset":"%s","conditionId":"%s","size":1500.25,"curPrice":0.6}]"""
                .formatted(asset, CONDITION_A)));
        server.enqueue(new MockResponse().setBody("""
                [{"asset":"%s","conditionId":"%s","size":12.5,"curPrice":0.6}]"""
                .formatted(asset, CONDITION_A)));
        server.enqueue(new MockResponse().setBody("""
                [{"asset":"%s","conditionId":"%s","size":0,"curPrice":0.6}]"""
                .formatted(asset, CONDITION_A)));

        try (Polymarket sdk = sdk()) {
            PositionQuery query = PositionQuery.forUser(USER).markets(List.of(CONDITION_A));
            assertEquals(new BigDecimal("1500.25"),
                    sdk.portfolio().positions(query).items().get(0).size());
            assertEquals(new BigDecimal("12.5"),
                    sdk.portfolio().positions(query).items().get(0).size());

            PositionSnapshot closed = sdk.portfolio().positions(query).items().get(0);
            assertEquals(new BigDecimal("0"), closed.size());
            assertEquals(Instant.parse("2026-08-16T09:30:00Z"), closed.observedAt());
        }
    }

    @Test
    void shouldThrowWhenPortfolioQueryBoundaryIsInvalid() throws Exception {
        try (Polymarket unused = sdk()) {
            // data-openapi.yaml Address: ^0x[a-fA-F0-9]{40}$
            assertThrows(IllegalArgumentException.class, () -> PositionQuery.forUser("0x1234"));
            assertThrows(IllegalArgumentException.class, () -> ActivityQuery.forUser("alice"));
            assertThrows(IllegalArgumentException.class, () -> TradeQuery.create().user("0x"));

            // data-openapi.yaml Hash64: ^0x[a-fA-F0-9]{64}$
            assertThrows(IllegalArgumentException.class,
                    () -> PositionQuery.forUser(USER).markets(List.of("0xaaa")));
            assertThrows(IllegalArgumentException.class,
                    () -> TradeQuery.create().markets(List.of(CONDITION_A, CONDITION_A)));

            // data-openapi.yaml sizeThreshold: minimum 0
            assertThrows(IllegalArgumentException.class,
                    () -> PositionQuery.forUser(USER).sizeThreshold(new BigDecimal("-0.5")));

            // data-openapi.yaml start/end: epoch seconds, minimum 0, and end bounds start
            assertThrows(IllegalArgumentException.class,
                    () -> TradeQuery.create().from(Instant.ofEpochSecond(-1)));
            assertThrows(IllegalArgumentException.class, () -> TradeQuery.create()
                    .from(Instant.ofEpochSecond(2_000)).to(Instant.ofEpochSecond(1_999)));
            assertThrows(IllegalArgumentException.class, () -> ActivityQuery.forUser(USER)
                    .to(Instant.ofEpochSecond(1_999)).from(Instant.ofEpochSecond(2_000)));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldEnforceEndpointPageBudgetsWhenPageIsRequested() throws Exception {
        enqueueFixture("trades.json");
        enqueueFixture("activity.json");

        try (Polymarket sdk = sdk()) {
            // constraints.json pagination.dataApi: trades 10000/10000, activity 500/5000.
            sdk.portfolio().trades(TradeQuery.create().user(USER), new PageCursor(10_000, 10_000));
            sdk.portfolio().activity(ActivityQuery.forUser(USER), new PageCursor(5_000, 500));

            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .trades(TradeQuery.create().user(USER), PageCursor.firstPage(10_001)));
            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .activity(ActivityQuery.forUser(USER), PageCursor.firstPage(501)));
        }

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void shouldMapActivityToTypedRowsWhenPageIsRead() throws Exception {
        enqueueFixture("activity.json");

        PortfolioPage<ActivityRecord> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().activity(ActivityQuery.forUser(USER));
        }

        ActivityRecord traded = page.items().get(0);
        assertTrue(traded.kind().is(ActivityKind.Known.TRADE));
        assertEquals(Instant.parse("2026-04-15T11:00:00Z"), traded.occurredAt());
        assertEquals(new BigDecimal("113.414225"), traded.usdcSize().orElseThrow());
        assertEquals(new BigDecimal("0.4523"), traded.price().orElseThrow());
        assertTrue(traded.side().orElseThrow().isBuy());
        assertEquals(Optional.empty(), traded.combo());

        ActivityRecord redeemed = page.items().get(1);
        assertTrue(redeemed.kind().is(ActivityKind.Known.REDEEM));
        assertEquals(new BigDecimal("0"), redeemed.usdcSize().orElseThrow());
        assertEquals(Optional.of(true), redeemed.combo());
        assertEquals(Optional.empty(), redeemed.side());
        assertEquals(Optional.of(999), redeemed.market().outcomeIndex());

        assertEquals("/activity?user=" + USER + "&limit=100&offset=0",
                server.takeRequest().getPath());
    }

    @Test
    void shouldPreserveUnknownActivityTypeWhenPageIsRead() throws Exception {
        enqueueFixture("activity.json");

        PortfolioPage<ActivityRecord> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().activity(ActivityQuery.forUser(USER));
        }

        ActivityRecord unknown = page.items().get(2);
        assertEquals("LOYALTY_AIRDROP", unknown.kind().raw());
        assertFalse(unknown.kind().isKnown());
        assertEquals(Optional.empty(), unknown.kind().known());
        assertFalse(unknown.kind().is(ActivityKind.Known.REWARD));
        assertEquals(Optional.empty(), unknown.conditionId());
        assertEquals(3, page.items().size());
    }

    @Test
    void shouldSendActivityFiltersWhenQueryIsRead() throws Exception {
        enqueueFixture("activity.json");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().activity(ActivityQuery.forUser(USER)
                    .kinds(List.of(ActivityKind.Known.TRADE, ActivityKind.Known.REDEEM))
                    .includeDepositsAndWithdrawals(true)
                    .side(Side.BUY)
                    .from(Instant.ofEpochSecond(1))
                    .to(Instant.ofEpochSecond(1776250800L)));

            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .activity(ActivityQuery.forUser(USER), new PageCursor(5001, 100)));
        }

        assertEquals("/activity?user=" + USER + "&type=TRADE%2CREDEEM"
                        + "&excludeDepositsWithdrawals=false&side=BUY&start=1&end=1776250800"
                        + "&limit=100&offset=0",
                server.takeRequest().getPath());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenNotificationsReadLacksCredentials() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(AuthenticationRequiredException.class,
                    () -> sdk.portfolio().notifications());
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldMapNotificationsWhenAuthenticatedReadSucceeds() throws Exception {
        enqueueFixture("notifications.json");

        List<Notification> notifications;
        try (Polymarket sdk = sdk(authorityWithCredentials(), SIGNED_AT)) {
            notifications = sdk.portfolio().notifications();
        }

        Notification filled = notifications.get(0);
        assertEquals(1L, filled.id());
        assertEquals("f4f247b7-4ac7-ff29-a152-04fda0a8755a", filled.owner().orElseThrow());
        assertTrue(filled.kind().is(NotificationKind.Known.ORDER_FILL));
        assertEquals(Instant.parse("2023-02-01T18:54:36Z"), filled.createdAt());
        assertEquals("0x72c66a1f70c00ac5e5eb9ce0452b7d118bc4869f8b822a1a8d8580c16e3ca83e",
                filled.payload().orderId().orElseThrow());
        assertEquals(new BigDecimal("0.6"), filled.payload().price().orElseThrow());
        assertEquals(new BigDecimal("90"), filled.payload().remainingSize().orElseThrow());
        assertTrue(filled.payload().side().orElseThrow().isSell());
        assertEquals(Optional.of(0), filled.payload().outcomeIndex());

        RecordedRequest request = server.takeRequest();
        assertEquals("/notifications?signature_type=0", request.getPath());
        assertEquals(SIGNER_ADDRESS, request.getHeader("POLY_ADDRESS"));
        assertEquals("f4f247b7-4ac7-ff29-a152-04fda0a8755a", request.getHeader("POLY_API_KEY"));
        assertEquals("hex-passphrase", request.getHeader("POLY_PASSPHRASE"));
        assertEquals("1773890758", request.getHeader("POLY_TIMESTAMP"));
        // HMAC over "1773890758GET/notifications?signature_type=0", computed with Python hmac.
        assertEquals("OQw7QmGMGaXg0JQvTSdtP5xoMYVsio8RfoVKIMX74ns=",
                request.getHeader("POLY_SIGNATURE"));
    }

    @Test
    void shouldPreserveUnknownNotificationTypeWhenPageIsRead() throws Exception {
        enqueueFixture("notifications.json");

        List<Notification> notifications;
        try (Polymarket sdk = sdk(authorityWithCredentials(), SIGNED_AT)) {
            notifications = sdk.portfolio().notifications();
        }

        Notification unknown = notifications.get(1);
        assertEquals(42, unknown.kind().code());
        assertFalse(unknown.kind().isKnown());
        assertEquals(Optional.of(""), unknown.owner());
        assertEquals(Optional.empty(), unknown.payload().orderId());
        assertEquals(2, notifications.size());
    }
}
