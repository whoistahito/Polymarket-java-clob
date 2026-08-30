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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trading: trade-ID settlement reconciliation (issue #16)")
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

    /** Advances a fixed step on every read so a poll loop reaches its deadline with no real sleep. */
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
    @DisplayName("TC-RC-001: a trade read sends the required maker filter and parses the page envelope")
    void everyTradeConfirmedIsConfirmed() throws Exception {
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
    @DisplayName("TC-RC-007: a page that does not carry the trade is continued at its next cursor")
    void anEnvelopeIsWalkedUntilTheCursorEnds() throws Exception {
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
    @DisplayName("TC-RC-002: any trade reaching FAILED makes the whole reconciliation Failed")
    void anyFailedTradeIsFailed() throws Exception {
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
    @DisplayName("TC-RC-003: CONFIRMED without a hash keeps polling until a later read carries one")
    void delayedTransactionHashResolvesOnALaterPoll() throws Exception {
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
    @DisplayName("TC-RC-004: a missing trade record is not terminal; the poll continues")
    void missingTradeRecordIsNotTerminal() throws Exception {
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
    @DisplayName("TC-RC-008: RETRYING is not terminal, so the poll waits for the retry to settle")
    void retryingIsNotTerminal() throws Exception {
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
    @DisplayName("TC-RC-009: two records for one trade id that disagree are Inconsistent, not Failed")
    void disagreeingDuplicateRecordsAreInconsistent() throws Exception {
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
    @DisplayName("TC-RC-010: a record contradicting itself is Inconsistent rather than collapsed to FAILED")
    void selfContradictingRecordIsInconsistent() throws Exception {
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
    @DisplayName("TC-RC-011: an absent required trade field stays absent and is reported Inconsistent")
    void absentRequiredFieldsAreNotFabricated() throws Exception {
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
    @DisplayName("TC-RC-005: a local timeout is Pending, retaining the order and trade ids, not a failure")
    void timeoutIsPendingNotFailure() throws Exception {
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
    @DisplayName("TC-RC-012: a Combo settlement keeps its RFQ id and every trade id in Pending")
    void pendingRetainsTheRfqAndAllTradeIds() throws Exception {
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
    @DisplayName("TC-RC-013: the local deadline is respected across the network work, not just the sleep")
    void aSlowResponseCannotOvershootTheDeadline() throws Exception {
        // The clock jumps a whole minute per reading, so the first read alone spends the budget.
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
    @DisplayName("TC-RC-016: Pending reports what each trade was last seen as, not just that it waited")
    void pendingCarriesTheLastObservedRecords() throws Exception {
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
    @DisplayName("TC-RC-017: the deadline stops the page walk, not only the poll between reads")
    void theDeadlineBoundsThePageWalkItself() throws Exception {
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
    @DisplayName("TC-RC-006: a status this release does not know keeps its raw value and is not terminal")
    void unknownStatusPreservesRawValue() throws Exception {
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
    @DisplayName("TC-RC-015: reconciling a sell settles against an absolute snapshot that may be zero")
    void positionReconciliationUsesAbsoluteSnapshots() throws Exception {
        enqueuePage("LTE=", confirmed("t1", "0xsold"));
        // The Data API always reports the CURRENT holding, so a closed-out position reads zero.
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
    @DisplayName("TC-RC-014: bad identifiers and durations are rejected before anything is sent")
    void identifiersAndDurationsAreValidatedBeforeSending() throws Exception {
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
    @DisplayName("TC-RC-015: a Proxy Trading Wallet signs as the Account Signer but filters on the wallet")
    void proxyWalletSignsAsSignerAndFiltersOnTheTradingWallet() throws Exception {
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
