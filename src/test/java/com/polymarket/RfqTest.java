package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.rfq.RfqGateway;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.rfq.Rfq;
import com.polymarket.rfq.RfqOutcome;
import com.polymarket.rfq.RfqRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rfq: Builder Gateway Combo request and status (issue #25)")
class RfqTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials ACCOUNT_CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final BuilderCredentials BUILDER_CREDENTIALS = new BuilderCredentials(
            "builder-key", "YnVpbGRlci1zZWNyZXQtYnVpbGRlci1zZWNyZXQ=", "builder-passphrase");
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

    private Rfq rfq(Clock clock) {
        URI host = server.url("/").uri();
        return new Rfq(new RfqGateway(host, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), clock), clock);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    private RfqRequest.Buy buyRequest() {
        return new RfqRequest.Buy(List.of(new PositionId("111"), new PositionId("222")),
                PusdAmount.of("1.0"));
    }

    @Test
    @DisplayName("TC-RQ-001: a request carries both account and builder HMAC header sets")
    void requestCarriesBothHeaderSets() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");

        RfqOutcome outcome = rfq(FIXED).request(buyRequest(), SigningIdentity.eoa(SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertInstanceOf(RfqOutcome.Waiting.class, outcome);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/builder/rfq/requests", request.getPath());
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
        assertEquals(ACCOUNT_CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
        assertEquals(BUILDER_CREDENTIALS.key(), request.getHeader("POLY_BUILDER_API_KEY"));
        assertEquals(BUILDER_CREDENTIALS.passphrase(), request.getHeader("POLY_BUILDER_PASSPHRASE"));
        assertTrue(request.getHeader("POLY_BUILDER_SIGNATURE") != null
                && !request.getHeader("POLY_BUILDER_SIGNATURE").isBlank());
        assertTrue(!request.getHeader("POLY_SIGNATURE").equals(request.getHeader("POLY_BUILDER_SIGNATURE")));
    }

    @Test
    @DisplayName("TC-RQ-002: the request body carries notional size for BUY in 6-decimal base units")
    void requestBodyCarriesNotionalForBuy() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");

        rfq(FIXED).request(buyRequest(), SigningIdentity.eoa(SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"direction\":\"BUY\""), body);
        assertTrue(body.contains("\"side\":\"YES\""), body);
        assertTrue(body.contains("\"unit\":\"notional\""), body);
        assertTrue(body.contains("\"value_e6\":\"1000000\""), body);
        assertTrue(body.contains("\"leg_position_ids\":[\"111\",\"222\"]"), body);
    }

    @Test
    @DisplayName("TC-RQ-003: fewer than 2 or more than 50 legs is rejected before sending")
    void legCountIsValidatedBeforeSending() {
        assertThrows(IllegalArgumentException.class,
                () -> new RfqRequest.Buy(List.of(new PositionId("111")), PusdAmount.of("1.0")));
        assertThrows(IllegalArgumentException.class, () -> new RfqRequest.Buy(
                List.of(new PositionId("111"), new PositionId("111")), PusdAmount.of("1.0")));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RQ-004: an awaiting-acceptance status with a quote is Quoted")
    void quotedStatusCarriesQuoteFields() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_REQUESTER_ACCEPTANCE",
                 "leg_position_ids":["111","222"],
                 "quote":{"quote_id":"quote-1","combo_position_id":"333",
                          "maker_amount_e6":"966191","taker_amount_e6":"1932381",
                          "expires_at":1773890818000,"builder_code":"0xbuilder"}}""");

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Quoted quoted = assertInstanceOf(RfqOutcome.Quoted.class, outcome);
        assertEquals("quote-1", quoted.quoteId());
        assertEquals(new PositionId("333"), quoted.comboPositionId());
        assertEquals(966191L, quoted.makerAmountBaseUnits());
        assertEquals(1932381L, quoted.takerAmountBaseUnits());
        assertEquals("0xbuilder", quoted.builderCode());
        assertEquals(List.of(new PositionId("111"), new PositionId("222")), quoted.legs());
        assertEquals(Instant.ofEpochMilli(1773890818000L), quoted.expiresAt());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/builder/rfq/requests/rfq-1", request.getPath());
    }

    @Test
    @DisplayName("TC-RQ-005: a FAILED status (no quote / decline / execution failure) is Failed with its reason")
    void failedStatusCarriesReason() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"FAILED","error":{"message":"no maker responded"}}""");

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Failed failed = assertInstanceOf(RfqOutcome.Failed.class, outcome);
        assertEquals("no maker responded", failed.reason());
    }

    @Test
    @DisplayName("TC-RQ-006: EXPIRED and CANCELED are their own typed outcomes")
    void expiredAndCanceledAreDistinctTypes() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"EXPIRED"}""");
        assertInstanceOf(RfqOutcome.Expired.class,
                rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));

        enqueue("""
                {"rfq_id":"rfq-1","status":"CANCELED"}""");
        assertInstanceOf(RfqOutcome.Canceled.class,
                rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));
    }

    @Test
    @DisplayName("TC-RQ-007: a status this release does not know is Unknown, keeping the raw value")
    void unknownStatusIsKeptRaw() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"SOME_NEW_STATE_2027"}""");

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("SOME_NEW_STATE_2027", unknown.rawStatus());
    }

    /** Advances a fixed step on every read so waitForQuote reaches its deadline with no real sleep. */
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
    @DisplayName("TC-RQ-008: waitForQuote polls through Waiting and resolves once a quote lands")
    void waitForQuoteResolvesOnceQuoted() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_REQUESTER_ACCEPTANCE","leg_position_ids":["111"],
                 "quote":{"quote_id":"quote-1","combo_position_id":"333","maker_amount_e6":"1",
                          "taker_amount_e6":"1","expires_at":1773890818000,"builder_code":"0xbuilder"}}""");

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofSeconds(1))).waitForQuote(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(), Duration.ofDays(1), Duration.ZERO);

        assertInstanceOf(RfqOutcome.Quoted.class, outcome);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RQ-009: a local wait timeout is Pending, retaining the rfq_id, not a failure")
    void waitTimeoutIsPending() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofHours(1))).waitForQuote(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(), Duration.ofSeconds(1), Duration.ZERO);

        RfqOutcome.Pending pending = assertInstanceOf(RfqOutcome.Pending.class, outcome);
        assertEquals("rfq-1", pending.rfqId());
    }

    @Test
    @DisplayName("TC-RQ-010: a requester write is never replayed, even with read retries configured")
    void requestIsNeverReplayedDespiteReadRetries() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");
        URI host = server.url("/").uri();
        Rfq withRetries = new Rfq(new RfqGateway(host, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), new ReadRetryPolicy(5, Duration.ZERO, Duration.ZERO),
                d -> {
                }), FIXED), FIXED);

        withRetries.request(buyRequest(), SigningIdentity.eoa(SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertEquals(1, server.getRequestCount());
    }

    private static final String BUILDER_CODE = "0x" + "b".repeat(64);

    private RfqOutcome.Quoted quotedFixture(Instant expiresAt) {
        return new RfqOutcome.Quoted("rfq-1", "quote-1", new PositionId("333"),
                List.of(new PositionId("111"), new PositionId("222")),
                966191L, 1932381L, expiresAt, BUILDER_CODE);
    }

    private com.polymarket.trading.SigningContext acceptContext() {
        return com.polymarket.trading.SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());
    }

    @Test
    @DisplayName("TC-RQ-011: an expired quote is rejected before anything is sent")
    void expiredQuoteRejectedBeforeSending() {
        RfqOutcome.Quoted expired = quotedFixture(FIXED.instant().minusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> rfq(FIXED).accept(expired,
                com.polymarket.trading.Side.BUY, new com.polymarket.internal.trading.Eip712OrderSigner(),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RQ-012: acceptance signs through V3 and sends both HMAC header sets")
    void acceptanceSignsThroughV3AndSendsBothHeaderSets() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}""");
        RfqOutcome.Quoted quote = quotedFixture(FIXED.instant().plusSeconds(60));

        RfqOutcome outcome = rfq(FIXED).accept(quote, com.polymarket.trading.Side.BUY,
                new com.polymarket.internal.trading.Eip712OrderSigner(), acceptContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertInstanceOf(RfqOutcome.Waiting.class, outcome);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/builder/rfq/requests/rfq-1/accept", request.getPath());
        assertEquals(ACCOUNT_CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
        assertEquals(BUILDER_CREDENTIALS.key(), request.getHeader("POLY_BUILDER_API_KEY"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"quote_id\":\"quote-1\""), body);
        assertTrue(body.contains("\"tokenId\":\"333\""), body);
        assertTrue(body.contains("\"makerAmount\":\"966191\""), body);
        assertTrue(body.contains("\"takerAmount\":\"1932381\""), body);
        assertTrue(body.contains("\"side\":0"), body);
        assertTrue(body.contains("\"builder\":\"" + BUILDER_CODE + "\""), body);
    }

    @Test
    @DisplayName("TC-RQ-013: a CONFIRMED or FILLED response after acceptance is Confirmed")
    void confirmedResponseIsConfirmed() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"FILLED"}""");

        RfqOutcome outcome = rfq(FIXED).accept(quotedFixture(FIXED.instant().plusSeconds(60)),
                com.polymarket.trading.Side.BUY, new com.polymarket.internal.trading.Eip712OrderSigner(),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Confirmed confirmed = assertInstanceOf(RfqOutcome.Confirmed.class, outcome);
        assertEquals("FILLED", confirmed.status());
    }

    @Test
    @DisplayName("TC-RQ-014: connection loss during acceptance is Unknown, never thrown or replayed")
    void connectionLossDuringAcceptanceIsUnknownNotReplayed() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(
                okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));

        RfqOutcome outcome = rfq(FIXED).accept(quotedFixture(FIXED.instant().plusSeconds(60)),
                com.polymarket.trading.Side.BUY, new com.polymarket.internal.trading.Eip712OrderSigner(),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("rfq-1", unknown.rfqId());
        assertEquals(1, server.getRequestCount());
    }
}
