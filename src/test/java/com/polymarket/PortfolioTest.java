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
import com.polymarket.portfolio.Notification;
import com.polymarket.portfolio.NotificationKind;
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
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio")
class PortfolioTest {

    private static final String USER = "0x56687bf447db6ffa42ffe2208a51ce8ba1a5b63a";
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
    @DisplayName("TC-PF-001: a position page is an absolute snapshot stamped with its observation time")
    void positionsAreAbsoluteSnapshotsWithObservationTime() throws Exception {
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
    @DisplayName("TC-PF-002: a sold-out position keeps its zero size instead of being clamped away")
    void zeroSizeIsPreserved() throws Exception {
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
    @DisplayName("TC-PF-003: a full page hands back the cursor its continuation must send")
    void aFullPageCarriesItsOwnNextCursor() throws Exception {
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
    @DisplayName("TC-PF-004: spending the offset budget is reported as neither complete nor continuable")
    void exhaustedOffsetBudgetIsExplicit() throws Exception {
        enqueueFixture("positions.json");

        PortfolioPage<PositionSnapshot> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().positions(PositionQuery.forUser(USER), new PageCursor(10_000, 2));
        }

        assertEquals(Optional.empty(), page.nextCursor());
        assertFalse(page.complete());
    }

    @Test
    @DisplayName("TC-PF-005: a page larger than the documented maximum fails before anything is sent")
    void oversizedPageIsRejectedBeforeSending() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .positions(PositionQuery.forUser(USER), PageCursor.firstPage(501)));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PF-006: every documented position filter reaches the wire in one query")
    void positionFiltersReachTheWire() throws Exception {
        enqueueFixture("positions.json");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().positions(PositionQuery.forUser(USER)
                    .markets(List.of("0xaaa", "0xbbb"))
                    .sizeThreshold(new BigDecimal("0.5"))
                    .redeemable(true)
                    .mergeable(false)
                    .includeArchived(true));
        }

        assertEquals("/positions?user=" + USER + "&market=0xaaa%2C0xbbb&sizeThreshold=0.5"
                + "&redeemable=true&mergeable=false&includeArchived=true&limit=100&offset=0",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-PF-007: a trade page carries exact amounts, its side and its execution time")
    void tradesMapToTypedRows() throws Exception {
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
    @DisplayName("TC-PF-008: trade filters and the trade offset budget follow the pinned constraints")
    void tradeFiltersAndBudgetFollowTheContract() throws Exception {
        enqueueFixture("trades.json");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().trades(TradeQuery.create()
                    .user(USER)
                    .markets(List.of("0xaaa"))
                    .side(Side.SELL)
                    .takerOnly(false)
                    .from(Instant.ofEpochSecond(1))
                    .to(Instant.ofEpochSecond(1776250800L)), PageCursor.firstPage(500));

            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .trades(TradeQuery.create().user(USER), new PageCursor(1001, 100)));
        }

        assertEquals("/trades?user=" + USER + "&market=0xaaa&side=SELL&takerOnly=false"
                        + "&start=1&end=1776250800&limit=500&offset=0",
                server.takeRequest().getPath());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PF-009: an activity page types its kind, amounts and combo flag")
    void activityMapsToTypedRows() throws Exception {
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
    @DisplayName("TC-PF-010: an activity type this release never heard of keeps its raw value")
    void unknownActivityTypeIsPreserved() throws Exception {
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
    @DisplayName("TC-PF-011: activity filters reach the wire and the offset budget is enforced")
    void activityFiltersAndBudgetFollowTheContract() throws Exception {
        enqueueFixture("activity.json");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().activity(ActivityQuery.forUser(USER)
                    .kinds(List.of(ActivityKind.Known.TRADE, ActivityKind.Known.REDEEM))
                    .includeDepositsAndWithdrawals(true)
                    .side(Side.BUY)
                    .from(Instant.ofEpochSecond(1))
                    .to(Instant.ofEpochSecond(1776250800L)));

            assertThrows(IllegalArgumentException.class, () -> sdk.portfolio()
                    .activity(ActivityQuery.forUser(USER), new PageCursor(1001, 100)));
        }

        assertEquals("/activity?user=" + USER + "&type=TRADE%2CREDEEM"
                        + "&excludeDepositsWithdrawals=false&side=BUY&start=1&end=1776250800"
                        + "&limit=100&offset=0",
                server.takeRequest().getPath());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PF-012: notifications without L2 credentials fail before anything is sent")
    void notificationsNeedL2Credentials() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(AuthenticationRequiredException.class,
                    () -> sdk.portfolio().notifications());
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PF-013: a notification read is L2-signed over its full path and types its payload")
    void notificationsAreL2SignedAndTyped() throws Exception {
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
    @DisplayName("TC-PF-014: a notification type this release never heard of keeps its raw code")
    void unknownNotificationTypeIsPreserved() throws Exception {
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
