package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.portfolio.PositionQuery;
import com.polymarket.portfolio.PositionSnapshot;
import com.polymarket.trading.ReconciliationOutcome;
import com.polymarket.trading.SettledTrade;
import com.polymarket.trading.TradeStatus;
import com.polymarket.trading.Side;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TradeReconciliationTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Instant START = Instant.ofEpochSecond(1_800_000_000L);
    private static final SigningIdentity EOA = SigningIdentity.eoa(SIGNER.address());
    private static final String MAKER_FILTER =
            "maker_address=0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

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

    private Polymarket sdk(Clock clock) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, clock);
    }

    /** clob-openapi.yaml TradesResponse: {limit, next_cursor, count, data}. "LTE=" ends the walk. */
    private void enqueuePage(String nextCursor, String... rows) {
        server.enqueue(new MockResponse().setBody("{\"limit\":100,\"next_cursor\":\"" + nextCursor
                + "\",\"count\":" + rows.length + ",\"data\":[" + String.join(",", rows) + "]}"));
    }

    private static String row(String id, String status, String extra) {
        return "{\"id\":\"" + id + "\",\"status\":\"" + status + "\",\"taker_order_id\":\"0xo1\","
                + "\"market\":\"0xm1\",\"asset_id\":\"123\",\"side\":\"BUY\",\"size\":\"10\","
                + "\"price\":\"0.52\",\"match_time\":\"1773890758\",\"last_update\":\"1773890758\","
                + "\"outcome\":\"Yes\",\"bucket_index\":0,\"owner\":\"owner-1\","
                + "\"maker_address\":\"0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266\","
                + "\"trader_side\":\"TAKER\"" + extra + "}";
    }

    private static String confirmed(String id, String hash) {
        return row(id, "TRADE_STATUS_CONFIRMED", ",\"transaction_hash\":\"" + hash + "\"");
    }

    private static final class SteppingClock extends Clock {
        private Instant now;
        private final Duration step;

        SteppingClock(Instant start, Duration step) {
            this.now = start;
            this.step = step;
        }

        @Override
        public Instant instant() {
            Instant t = now;
            now = now.plus(step);
            return t;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void shouldConfirmTradesWhenEveryTradeReachesConfirmed() throws Exception {
        enqueuePage("LTE=", confirmed("t1", "0xhash1"));
        enqueuePage("LTE=", confirmed("t2", "0xhash2"));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1", "t2"), Duration.ofSeconds(30), Duration.ZERO);
        }

        ReconciliationOutcome.Confirmed confirmed =
                assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals(2, confirmed.trades().size());

        SettledTrade first = confirmed.trades().get(0);
        assertEquals("0xhash1", first.transactionHash().orElseThrow());
        assertEquals(Optional.of(Side.BUY), first.side());
        assertEquals(Optional.of(new BigDecimal("10")), first.size());
        assertEquals(Optional.of(new BigDecimal("0.52")), first.price());
        assertEquals(Optional.of(Instant.ofEpochSecond(1773890758L)), first.matchTime());
        assertEquals(Optional.empty(), first.errorMessage());

        assertEquals(2, server.getRequestCount());
        // trades.json clobTradeRead: maker_address is required:true — id alone is a 400.
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/data/trades?id=t1&" + MAKER_FILTER, request.getPath());
        // HMAC over "1800000000GET/data/trades?id=t1&maker_address=0xf39f...", Python hmac.
        assertEquals("_uON0btIIFNlObFl8SszxwjpZ__eMemLElls1UEnKag=",
                request.getHeader("POLY_SIGNATURE"));
        assertEquals("/data/trades?id=t2&" + MAKER_FILTER, server.takeRequest().getPath());
    }

    @Test
    void shouldWalkTradePagesWhenCursorContinues() throws Exception {
        enqueuePage("MTAw", confirmed("other", "0xother"));
        enqueuePage("LTE=", confirmed("t1", "0xhash1"));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(30), Duration.ZERO);
        }

        assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals(2, server.getRequestCount());
        assertEquals("/data/trades?id=t1&" + MAKER_FILTER, server.takeRequest().getPath());
        assertEquals("/data/trades?id=t1&" + MAKER_FILTER + "&next_cursor=MTAw",
                server.takeRequest().getPath());
    }

    @Test
    void shouldFailReconciliationWhenAnyTradeFails() throws Exception {
        enqueuePage("LTE=", confirmed("t1", "0xhash1"));
        enqueuePage("LTE=", row("t2", "TRADE_STATUS_FAILED", ",\"err_msg\":\"not enough balance\""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1", "t2"), Duration.ofSeconds(30), Duration.ZERO);
        }

        ReconciliationOutcome.Failed failed =
                assertInstanceOf(ReconciliationOutcome.Failed.class, outcome);
        assertEquals(Optional.of("not enough balance"), failed.trades().get(1).errorMessage());
    }

    @Test
    void shouldPollUntilHashAppearsWhenConfirmedTradeLacksHash() throws Exception {
        // changelog 2026-07-17: poll by trade ID until each has a hash or returns FAILED.
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_CONFIRMED", ""));
        enqueuePage("LTE=", confirmed("t1", "0xlate"));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofSeconds(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofDays(1), Duration.ZERO);
        }

        ReconciliationOutcome.Confirmed confirmed =
                assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals("0xlate", confirmed.trades().get(0).transactionHash().orElseThrow());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void shouldPollUntilTradeAppearsWhenRecordIsMissing() throws Exception {
        enqueuePage("LTE=");
        enqueuePage("LTE=", confirmed("t1", "0xhash1"));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofSeconds(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofDays(1), Duration.ZERO);
        }

        assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void shouldPollUntilSettlementWhenTradeIsRetrying() throws Exception {
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_RETRYING", ""));
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_MINED", ""));
        enqueuePage("LTE=", confirmed("t1", "0xretried"));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofSeconds(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofDays(1), Duration.ZERO);
        }

        assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void shouldReportInconsistentWhenDuplicateTradeRecordsDisagree() throws Exception {
        enqueuePage("LTE=", confirmed("t1", "0xhash1"),
                row("t1", "TRADE_STATUS_FAILED", ""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(30), Duration.ZERO);
        }

        ReconciliationOutcome.Inconsistent inconsistent =
                assertInstanceOf(ReconciliationOutcome.Inconsistent.class, outcome);
        assertEquals(1, inconsistent.contradictions().size());
        assertTrue(inconsistent.contradictions().get(0).contains("t1"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldReportInconsistentWhenTradeRecordContradictsItself() throws Exception {
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_CONFIRMED",
                ",\"transaction_hash\":\"0xhash1\",\"err_msg\":\"execution reverted\""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(30), Duration.ZERO);
        }

        assertInstanceOf(ReconciliationOutcome.Inconsistent.class, outcome);
    }

    @Test
    void shouldReportInconsistentWhenRequiredTradeFieldsAreMissing() throws Exception {
        enqueuePage("LTE=",
                "{\"id\":\"t1\",\"status\":\"TRADE_STATUS_CONFIRMED\",\"transaction_hash\":\"0xh\"}");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(30), Duration.ZERO);
        }

        ReconciliationOutcome.Inconsistent inconsistent =
                assertInstanceOf(ReconciliationOutcome.Inconsistent.class, outcome);
        SettledTrade sparse = inconsistent.trades().get(0);
        assertEquals(Optional.empty(), sparse.side());
        assertEquals(Optional.empty(), sparse.size());
        assertEquals(Optional.empty(), sparse.price());
        assertEquals(Optional.empty(), sparse.assetId());
    }

    @Test
    void shouldReturnPendingWhenReconciliationTimesOut() throws Exception {
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_MATCHED", ""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofHours(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(1), Duration.ZERO);
        }

        ReconciliationOutcome.Pending pending =
                assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
        assertEquals("order-1", pending.orderId());
        assertEquals(List.of("t1"), pending.tradeIds());
        assertTrue(pending.rfqId().isEmpty());
    }

    @Test
    void shouldRetainRfqAndTradeIdsWhenReconciliationIsPending() throws Exception {
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_MATCHED", ""));
        enqueuePage("LTE=", row("t2", "TRADE_STATUS_MINED", ""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofHours(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1", "rfq-7",
                    List.of("t1", "t2"), Duration.ofSeconds(1), Duration.ZERO);
        }

        ReconciliationOutcome.Pending pending =
                assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
        assertEquals("order-1", pending.orderId());
        assertEquals(Optional.of("rfq-7"), pending.rfqId());
        assertEquals(List.of("t1", "t2"), pending.tradeIds());
    }

    @Test
    void shouldReturnPendingWhenNetworkWorkSpendsDeadline() throws Exception {
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_MATCHED", ""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofMinutes(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(30), Duration.ofHours(2));
        }

        assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldRetainLastObservedTradesWhenReconciliationIsPending() throws Exception {
        // t1 is matched but not mined, t2 has no record at all. Both are non-terminal, and a
        // caller deciding whether to wait or investigate needs to be able to tell them apart.
        enqueuePage("LTE=", row("t1", "TRADE_STATUS_MATCHED", ""));
        enqueuePage("LTE=");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofSeconds(20)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1", "t2"), Duration.ofSeconds(30), Duration.ofHours(2));
        }

        ReconciliationOutcome.Pending pending =
                assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
        assertEquals(List.of("t1", "t2"), pending.tradeIds());
        assertEquals(List.of("t1"), pending.observed().stream().map(SettledTrade::id).toList(),
                "the matched trade was seen; the other one is missing, and that is a fact too");
        assertTrue(pending.observed().get(0).status().is(TradeStatus.Known.MATCHED),
                "MATCHED must not be collapsed into an unexplained wait");
    }

    @Test
    void shouldStopPageWalkWhenDeadlineIsSpent() throws Exception {
        // Three pages for one trade id. The clock spends the whole budget reading the first, so
        // the walk must stop there rather than finishing a chain the caller no longer has time for.
        enqueuePage("cursor-2", row("t1", "TRADE_STATUS_MATCHED", ""));
        enqueuePage("cursor-3", row("t1", "TRADE_STATUS_MATCHED", ""));
        enqueuePage("LTE=", confirmed("t1", "0xhash"));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofMinutes(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(30), Duration.ofHours(2));
        }

        assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
        assertEquals(1, server.getRequestCount(),
                "the page walk must respect the deadline it is running under");
    }

    @Test
    void shouldPreserveUnknownStatusWhenTradeIsNonTerminal() throws Exception {
        enqueuePage("LTE=", row("t1", "SETTLING_NEW_2027", ""));

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(START, Duration.ofHours(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                    List.of("t1"), Duration.ofSeconds(1), Duration.ZERO);
        }

        // Unknown status is not terminal, so the poll runs out the clock: Pending, not a crash.
        assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
    }

    @Test
    void shouldUseAbsolutePositionSnapshotWhenSellSettles() throws Exception {
        enqueuePage("LTE=", confirmed("t1", "0xsold"));
        // Data API reports the current holding, so a closed position reads zero.
        server.enqueue(new MockResponse().setBody(
                "[{\"asset\":\"123\",\"conditionId\":\"0x" + "b".repeat(64) + "\",\"size\":0}]"));

        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            assertInstanceOf(ReconciliationOutcome.Confirmed.class,
                    sdk.trading().reconcile(CREDENTIALS, EOA, "order-1",
                            List.of("t1"), Duration.ofSeconds(30), Duration.ZERO));

            PositionSnapshot after = sdk.portfolio()
                    .positions(PositionQuery.forUser(SIGNER.address())).items().get(0);
            assertEquals(0, after.size().signum());
            assertEquals(START, after.observedAt());
        }
    }

    @Test
    void shouldThrowWhenReconciliationInputsAreInvalid() throws Exception {
        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            var trading = sdk.trading();
            assertThrows(IllegalArgumentException.class, () -> SigningIdentity.eoa("0xnope"));
            assertThrows(IllegalArgumentException.class, () -> trading.reconcile(CREDENTIALS,
                    EOA, " ", List.of("t1"), Duration.ofSeconds(1), Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> trading.reconcile(CREDENTIALS,
                    EOA, "order-1", List.of(), Duration.ofSeconds(1), Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> trading.reconcile(CREDENTIALS,
                    EOA, "order-1", List.of("t1", "t1"), Duration.ofSeconds(1),
                    Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> trading.reconcile(CREDENTIALS,
                    EOA, "order-1", List.of("t1"), Duration.ofSeconds(-1),
                    Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> trading.reconcile(CREDENTIALS,
                    EOA, "order-1", List.of("t1"), Duration.ofSeconds(1),
                    Duration.ofSeconds(-1)));
            assertThrows(IllegalArgumentException.class, () -> trading.reconcile(CREDENTIALS,
                    EOA, "order-1", " ", List.of("t1"), Duration.ofSeconds(1),
                    Duration.ZERO));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldFilterByTradingWalletWhenProxyWalletSignsRequest() throws Exception {
        SigningIdentity proxy = SigningIdentity.deriveProxyWallet(SIGNER.address());
        server.enqueue(new MockResponse().setBody(
                "{\"limit\":100,\"next_cursor\":\"LTE=\",\"count\":0,\"data\":[]}"));

        try (Polymarket sdk = sdk(Clock.fixed(START, ZoneOffset.UTC))) {
            sdk.trading().reconcile(CREDENTIALS, proxy, "order-1", List.of("t1"),
                    Duration.ZERO, Duration.ZERO);
        }

        // POLY_ADDRESS is the address associated with the API key (the Account Signer);
        // maker_address filters on the maker of the order (the Trading Wallet). They differ here.
        RecordedRequest request = server.takeRequest();
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
        assertTrue(request.getPath().contains("maker_address=" + proxy.tradingWallet()),
                request.getPath());
        assertTrue(!proxy.tradingWallet().equalsIgnoreCase(proxy.accountSigner()));
    }
}
