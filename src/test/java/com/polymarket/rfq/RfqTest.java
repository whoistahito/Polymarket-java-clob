package com.polymarket.rfq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.ReadRetryPolicy;
import com.polymarket.internal.rfq.ComboMarketGateway;
import com.polymarket.internal.rfq.RfqGateway;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.trading.Side;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        return rfq(clock, d -> {
        });
    }

    private Rfq rfq(Clock clock, Rfq.Sleeper sleeper) {
        URI host = server.url("/").uri();
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        return new Rfq(new RfqGateway(host, runtime, clock),
                new ComboMarketGateway(host, runtime), new Eip712OrderSigner(), clock, sleeper);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    private RfqRequest.Buy buyRequest() {
        return new RfqRequest.Buy(List.of(new PositionId("111"), new PositionId("222")),
                PusdAmount.of("1.0"));
    }

    private RfqRequest.Sell officialRequest() {
        return new RfqRequest.Sell(List.of(new PositionId("111"), new PositionId("222")),
                ShareQuantity.of("1.932381"));
    }

    @Test
    void shouldCarryBothHeaderSetsWhenRequesting() throws Exception {
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
    void shouldCarryBuyNotionalInBodyWhenRequesting() throws Exception {
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

    private static final String TRADING_WALLET = "0x1234567890abcdef1234567890abcdef12345678";

    @Test
    void shouldCarryTradingWalletInBothAddressFieldsWhenRequestingAsDepositWallet() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");

        rfq(FIXED).request(buyRequest(),
                SigningIdentity.depositWallet(TRADING_WALLET, SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"signer_address\":\"" + TRADING_WALLET + "\""), body);
        assertTrue(body.contains("\"maker_address\":\"" + TRADING_WALLET + "\""), body);
        assertTrue(body.contains("\"signature_type\":3"), body);
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
    }

    @Test
    void shouldKeepAccountSignerInSignerAddressWhenRequestingAsProxyWallet() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");

        rfq(FIXED).request(buyRequest(),
                SigningIdentity.proxyWallet(TRADING_WALLET, SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"signer_address\":\"" + SIGNER.address() + "\""), body);
        assertTrue(body.contains("\"maker_address\":\"" + TRADING_WALLET + "\""), body);
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenLegCountIsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new RfqRequest.Buy(List.of(new PositionId("111")), PusdAmount.of("1.0")));
        assertThrows(IllegalArgumentException.class, () -> new RfqRequest.Buy(
                List.of(new PositionId("111"), new PositionId("111")), PusdAmount.of("1.0")));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldReturnNotYetAcceptedWhenStatusIsReadBeforeAcceptance() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("""
                {"error":"rfq has not been accepted"}"""));

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.NotYetAccepted notYet =
                assertInstanceOf(RfqOutcome.NotYetAccepted.class, outcome);
        assertEquals("rfq-1", notYet.rfqId());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/builder/rfq/requests/rfq-1", request.getPath());
        assertEquals(null, request.getHeader("POLY_BUILDER_API_KEY"));
    }

    /** Protocol shape pinned by builder-gateway.json: expiry and builder are top-level; IDs are under request. */
    private static final String OFFICIAL_CREATE_RESPONSE = """
            {"rfq_id":"rfq-1",
             "status":"AWAITING_REQUESTER_ACCEPTANCE",
             "expires_at":1773890765500,
             "builder_code":"0xbuilder",
             "request":{"rfq_id":"rfq-1","maker_address":"0xmaker","requestor_public_id":"pub-1",
                        "leg_position_ids":["111","222"],"condition_id":"0xcond",
                        "yes_position_id":"333","no_position_id":"444","direction":"SELL",
                        "side":"YES","requested_size":{"unit":"shares","value_e6":"1932381"},
                        "created_at":1773890758000},
             "quote":{"quote_id":"quote-1","blended_price_e6":"500000",
                      "maker_amount_e6":"966191","taker_amount_e6":"1932381",
                      "total_required_e6":"1932381","net_receive_e6":"950000"}}""";

    @Test
    void shouldMapTopLevelExpiryAndBuilderCodeWhenCreateResponseIsOfficial() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE);

        RfqOutcome outcome = rfq(FIXED).request(officialRequest(),
                SigningIdentity.eoa(SIGNER.address()), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Quoted quoted = assertInstanceOf(RfqOutcome.Quoted.class, outcome);
        assertEquals(Instant.ofEpochMilli(1773890765500L), quoted.expiresAt());
        assertEquals("0xbuilder", quoted.builderCode());
    }

    @Test
    void shouldMapComboPositionAndLegsFromRequestBlockWhenCreateResponseIsOfficial() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE);

        RfqOutcome.Quoted quoted = assertInstanceOf(RfqOutcome.Quoted.class,
                rfq(FIXED).request(officialRequest(), SigningIdentity.eoa(SIGNER.address()),
                        ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));

        assertEquals(new PositionId("333"), quoted.comboPositionId());
        assertEquals(List.of(new PositionId("111"), new PositionId("222")), quoted.legs());
    }

    @Test
    void shouldMapEveryQuoteAmountWhenCreateResponseIncludesQuote() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE);

        RfqOutcome.Quoted quoted = assertInstanceOf(RfqOutcome.Quoted.class,
                rfq(FIXED).request(officialRequest(), SigningIdentity.eoa(SIGNER.address()),
                        ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));

        assertEquals("quote-1", quoted.quoteId());
        assertEquals(500000L, quoted.amounts().blendedPriceBaseUnits());
        assertEquals(966191L, quoted.amounts().makerAmountBaseUnits());
        assertEquals(1932381L, quoted.amounts().takerAmountBaseUnits());
        assertEquals(1932381L, quoted.amounts().totalRequiredBaseUnits());
        assertEquals(950000L, quoted.amounts().netReceiveBaseUnits());
    }

    @Test
    void shouldRetainRequestDirectionWhenQuoteIsValid() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE);

        RfqOutcome.Quoted quoted = assertInstanceOf(RfqOutcome.Quoted.class,
                rfq(FIXED).request(officialRequest(), SigningIdentity.eoa(SIGNER.address()),
                        ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));

        assertEquals(Side.SELL, quoted.direction());
    }

    @Test
    void shouldReturnUnknownWhenQuoteOmitsDirection() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE.replace("\"direction\":\"SELL\",", ""));

        RfqOutcome outcome = rfq(FIXED).request(officialRequest(),
                SigningIdentity.eoa(SIGNER.address()), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertInstanceOf(RfqOutcome.Unknown.class, outcome,
                "a Quote whose direction the wire omitted states nothing about which way to sign");
    }

    @Test
    void shouldReturnUnknownWhenQuoteOmitsSigningAmount() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE.replace("\"maker_amount_e6\":\"966191\",", ""));

        RfqOutcome outcome = rfq(FIXED).request(officialRequest(),
                SigningIdentity.eoa(SIGNER.address()), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertInstanceOf(RfqOutcome.Unknown.class, outcome,
                "signing a zero maker amount would be an order the gateway never quoted");
    }

    @Test
    void shouldReturnFailureReasonWhenStatusIsFailed() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"FAILED","error":{"message":"no maker responded"}}""");

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Failed failed = assertInstanceOf(RfqOutcome.Failed.class, outcome);
        assertEquals("no maker responded", failed.reason());
    }

    @Test
    void shouldReturnDistinctOutcomesWhenStatusIsExpiredOrCanceled() throws Exception {
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
    void shouldKeepRawStatusWhenStatusIsUnknown() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"SOME_NEW_STATE_2027"}""");

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("SOME_NEW_STATE_2027", unknown.rawStatus());
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
    void shouldPollToTerminalOutcomeWhenAwaitingSettlement() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");
        enqueue("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}""");
        enqueue("""
                {"rfq_id":"rfq-1","status":"CONFIRMED","tx_hash":"0xdead"}""");

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofSeconds(1))).awaitSettlement(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(), Duration.ofDays(1), Duration.ZERO);

        assertInstanceOf(RfqOutcome.Confirmed.class, outcome);
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void shouldReturnPendingWhenSettlementWaitTimesOut() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofHours(1))).awaitSettlement(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(), Duration.ofSeconds(1), Duration.ZERO);

        RfqOutcome.Pending pending = assertInstanceOf(RfqOutcome.Pending.class, outcome);
        assertEquals("rfq-1", pending.rfqId());
    }

    @Test
    void shouldClampPollingDelayWhenSettlementDeadlineIsNear() throws Exception {
        for (int i = 0; i < 5; i++) {
            enqueue("""
                    {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");
        }
        List<Duration> waits = new ArrayList<>();

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofMillis(200)), waits::add).awaitSettlement(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(),
                Duration.ofSeconds(1), Duration.ofHours(1));

        assertInstanceOf(RfqOutcome.Pending.class, outcome);
        assertTrue(waits.stream().mapToLong(Duration::toMillis).sum() <= 1_000L,
                "an hour-long poll interval must be trimmed to the time actually left: " + waits);
    }

    @Test
    void shouldReturnUnknownWhenQuoteOmitsRequiredField() throws Exception {
        for (String missing : List.of("quote_id", "yes_position_id", "expires_at", "builder_code",
                "leg_position_ids")) {
            enqueue(OFFICIAL_CREATE_RESPONSE
                    .replaceAll("\"" + missing + "\":\\[[^]]*],", "")
                    .replaceAll("\"" + missing + "\":\"[^\"]*\",", "")
                    .replaceAll("\"" + missing + "\":[0-9]+,", ""));

            RfqOutcome outcome = rfq(FIXED).request(officialRequest(),
                    SigningIdentity.eoa(SIGNER.address()), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

            assertInstanceOf(RfqOutcome.Unknown.class, outcome,
                    "a response without " + missing + " is not something to accept");
        }
    }

    @Test
    void shouldReturnUnknownWhenQuoteReversesRequestDirection() throws Exception {
        enqueue(OFFICIAL_CREATE_RESPONSE);

        RfqOutcome outcome = rfq(FIXED).request(buyRequest(),
                SigningIdentity.eoa(SIGNER.address()), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertInstanceOf(RfqOutcome.Unknown.class, outcome);
    }

    @Test
    void shouldReturnUnknownWhenQuoteContainsMalformedNumbers() throws Exception {
        for (String response : List.of(
                OFFICIAL_CREATE_RESPONSE.replace("\"maker_amount_e6\":\"966191\"",
                        "\"maker_amount_e6\":\"-1\""),
                OFFICIAL_CREATE_RESPONSE.replace("\"expires_at\":1773890765500",
                        "\"expires_at\":1773890765500.5"))) {
            enqueue(response);
            RfqOutcome outcome = rfq(FIXED).request(officialRequest(),
                    SigningIdentity.eoa(SIGNER.address()), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);
            assertInstanceOf(RfqOutcome.Unknown.class, outcome, response);
        }
    }

    @Test
    void shouldContinueWaitingWhenGatewayErrorIsTransient() throws Exception {
        // 503 is the gateway declining to answer, not a verdict on an RFQ that is already accepted
        // and executing. Reporting it as Rejected would call a live trade refused.
        server.enqueue(new MockResponse().setResponseCode(503).setBody("""
                {"rfq_id":"rfq-1","code":"unavailable","error":{"message":"try again"}}"""));
        enqueue("""
                {"rfq_id":"rfq-1","status":"CONFIRMED","tx_hash":"0xdead"}""");

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofSeconds(1))).awaitSettlement(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(), Duration.ofDays(1), Duration.ZERO);

        assertInstanceOf(RfqOutcome.Confirmed.class, outcome);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void shouldReturnRejectedWhenGatewayRefusalIsDefinitive() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("""
                {"rfq_id":"rfq-1","code":"forbidden","error":{"message":"not your rfq"}}"""));

        RfqOutcome outcome = rfq(new SteppingClock(Instant.ofEpochSecond(1_800_000_000),
                Duration.ofSeconds(1))).awaitSettlement(
                "rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address(), Duration.ofDays(1), Duration.ZERO);

        assertInstanceOf(RfqOutcome.Rejected.class, outcome);
        assertEquals(1, server.getRequestCount(), "polling a refusal that cannot change is a spin");
    }

    @Test
    void shouldSendRequestOnceWhenReadRetriesAreConfigured() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}""");
        URI host = server.url("/").uri();
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                new ReadRetryPolicy(5, Duration.ZERO, Duration.ZERO), d -> {
                });
        Rfq withRetries = new Rfq(new RfqGateway(host, runtime, FIXED),
                new ComboMarketGateway(host, runtime), new Eip712OrderSigner(), FIXED);

        withRetries.request(buyRequest(), SigningIdentity.eoa(SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        assertEquals(1, server.getRequestCount());
    }

    private static final String BUILDER_CODE = "0x" + "b".repeat(64);

    /** Amounts pinned in builder-gateway.json's acceptRequestBody example. */
    private RfqOutcome.Quoted quotedFixture(Instant expiresAt) {
        return quotedFixture(expiresAt, Side.BUY);
    }

    private RfqOutcome.Quoted quotedFixture(Instant expiresAt, Side direction) {
        return new RfqOutcome.Quoted("rfq-1", "quote-1", direction, new PositionId("333"),
                List.of(new PositionId("111"), new PositionId("222")),
                new QuoteAmounts(500000L, 966191L, 1932381L, 966191L, 1932381L),
                expiresAt, BUILDER_CODE, acceptContext().identity());
    }

    private com.polymarket.trading.SigningContext acceptContext() {
        return com.polymarket.trading.SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenQuoteIsExpired() {
        RfqOutcome.Quoted expired = quotedFixture(FIXED.instant().minusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> rfq(FIXED).accept(expired,
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldSignThroughV3AndSendBothHeaderSetsWhenAccepting() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}""");
        RfqOutcome.Quoted quote = quotedFixture(FIXED.instant().plusSeconds(60));

        RfqOutcome outcome = rfq(FIXED).accept(quote,
                acceptContext(),
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
    void shouldReturnConfirmedWhenAcceptanceResponseIsFilled() throws Exception {
        enqueue("""
                {"rfq_id":"rfq-1","status":"FILLED"}""");

        RfqOutcome outcome = rfq(FIXED).accept(quotedFixture(FIXED.instant().plusSeconds(60)),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Confirmed confirmed = assertInstanceOf(RfqOutcome.Confirmed.class, outcome);
        assertEquals("FILLED", confirmed.status());
    }

    @Test
    void shouldReturnUnknownWithoutReplayWhenAcceptanceLosesConnection() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(
                okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));

        RfqOutcome outcome = rfq(FIXED).accept(quotedFixture(FIXED.instant().plusSeconds(60)),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("rfq-1", unknown.rfqId());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldReturnRejectedWhenHttpValidationFails() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("""
                {"rfq_id":"rfq-1","code":"BUILDER_CODE_DISABLED",
                 "error":"the builder key has no enabled builder code"}"""));

        RfqOutcome outcome = rfq(FIXED).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Rejected rejected = assertInstanceOf(RfqOutcome.Rejected.class, outcome);
        assertEquals("rfq-1", rejected.rfqId());
        assertEquals(403, rejected.httpStatus());
        assertTrue(rejected.reason().contains("the builder key has no enabled builder code"),
                rejected.reason());
    }

    @Test
    void shouldReturnRejectedWhenAcceptanceReturnsConflict() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("""
                {"rfq_id":"rfq-1","code":"QUOTE_MISMATCH","error":"quote mismatch"}"""));

        RfqOutcome outcome = rfq(FIXED).accept(quotedFixture(FIXED.instant().plusSeconds(60)),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Rejected rejected = assertInstanceOf(RfqOutcome.Rejected.class, outcome);
        assertEquals(409, rejected.httpStatus());
        assertEquals("rfq-1", rejected.rfqId());
    }

    @Test
    void shouldKeepRfqIdWhenAcceptanceBodyIsUnreadable() throws Exception {
        server.enqueue(new MockResponse().setBody("<html>gateway</html>"));

        RfqOutcome outcome = rfq(FIXED).accept(quotedFixture(FIXED.instant().plusSeconds(60)),
                acceptContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("rfq-1", unknown.rfqId());
    }

    @Test
    void shouldThrowRfqGatewayExceptionWhenCreateIsRefusedBeforeRfqExists() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("""
                {"code":"INVALID_LEGS","error":"leg position ids are not compatible"}"""));

        RfqGatewayException refused = assertThrows(RfqGatewayException.class,
                () -> rfq(FIXED).request(buyRequest(), SigningIdentity.eoa(SIGNER.address()),
                        ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));

        assertEquals(400, refused.httpStatus());
        assertTrue(refused.getMessage().contains("leg position ids are not compatible"),
                refused.getMessage());
    }
}
