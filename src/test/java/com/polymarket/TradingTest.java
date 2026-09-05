package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.GoodTilDateOrder;
import com.polymarket.trading.LimitOrder;
import com.polymarket.trading.MakerOnlyLimitOrder;
import com.polymarket.trading.OrderExecution;
import com.polymarket.trading.OrderPlacement;
import com.polymarket.trading.OrderType;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import com.polymarket.trading.SubmissionOutcome;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TradingTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("1"), false);

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
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority(), FIXED);
    }

    private static com.polymarket.authentication.SigningAuthority authority() {
        return com.polymarket.authentication.SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
    }

    private static SigningContext context() {
        return SigningContext.of(SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());
    }

    private SignedOrder signedOrder() {
        SigningContext context = context();
        try (Polymarket sdk = sdk()) {
            return sdk.trading().sign(new TokenId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
        }
    }

    private void enqueue(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
    }

    @Test
    void shouldAcceptCoherentSuccessWhenOrderResponseIsComplete() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc123","status":"matched",
                 "tradeIDs":["0xtrade1","0xtrade2"],"makingAmount":"5.2","takingAmount":"10"}""");

        SubmissionOutcome outcome;
        SignedOrder order = signedOrder();
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submit(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Accepted accepted = assertInstanceOf(SubmissionOutcome.Accepted.class, outcome);
        assertEquals("0xabc123", accepted.orderId());
        assertEquals("matched", accepted.status());
        assertEquals(java.util.List.of("0xtrade1", "0xtrade2"), accepted.tradeIds());
        assertEquals("5.2", accepted.makingAmount().orElseThrow());

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/order", request.getPath());
        assertEquals(CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"orderType\":\"GTC\""), body);
        assertTrue(body.contains("\"postOnly\":false"), body);
        assertTrue(body.contains("\"owner\":\"" + CREDENTIALS.key() + "\""), body);
    }

    @Test
    void shouldRejectWhenOrderResponseExplicitlyFails() throws Exception {
        enqueue(400, """
                {"success":false,"errorMsg":"invalid signature"}""");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Rejected rejected = assertInstanceOf(SubmissionOutcome.Rejected.class, outcome);
        assertEquals(400, rejected.httpStatus());
        assertEquals("invalid signature", rejected.reason());
        assertTrue(!rejected.safeToRetry());
    }

    @Test
    void shouldMarkTimeoutRetryableWhenServerReportsOrderTimedOut() throws Exception {
        enqueue(500, "order timed out");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Rejected rejected = assertInstanceOf(SubmissionOutcome.Rejected.class, outcome);
        assertEquals(500, rejected.httpStatus());
        assertTrue(rejected.safeToRetry());
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenServerFailureIsGeneric() throws Exception {
        enqueue(502, "upstream unavailable");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Unknown unknown = assertInstanceOf(SubmissionOutcome.Unknown.class, outcome);
        assertEquals(502, unknown.httpStatus().orElseThrow());
    }

    @Test
    void shouldRejectWhenServiceReportsCancelOnly() throws Exception {
        enqueue(503, "trading is currently cancel-only");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Rejected rejected = assertInstanceOf(SubmissionOutcome.Rejected.class, outcome);
        assertEquals(503, rejected.httpStatus());
        assertTrue(rejected.safeToRetry());
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenOrderIsDuplicated() throws Exception {
        enqueue(400, """
                {"success":false,"errorMsg":"order 0xabc is invalid. Duplicated."}""");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        assertInstanceOf(SubmissionOutcome.Unknown.class, outcome);
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenConnectionIsLost() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Unknown unknown = assertInstanceOf(SubmissionOutcome.Unknown.class, outcome);
        assertTrue(unknown.cause().isPresent());
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenSuccessLacksOrderId() throws Exception {
        enqueue(200, """
                {"success":true,"status":"live"}""");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        assertInstanceOf(SubmissionOutcome.Unknown.class, outcome);
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenSuccessContradictsItself() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"live","errorMsg":"partial issue"}""");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        assertInstanceOf(SubmissionOutcome.Unknown.class, outcome);
    }

    @Test
    void shouldThrowWhenPositionOrderIsSubmitted() throws Exception {
        SigningContext context = SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());
        SignedOrder positionOrder;
        try (Polymarket sdk = sdk()) {
            positionOrder = sdk.trading().sign(new PositionId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
        }

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading()
                    .submit(positionOrder, OrderPlacement.of(CREDENTIALS, OrderType.GTC)));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldSubmitOnceWhenReadRetriesAreConfigured() throws Exception {
        enqueue(502, "upstream unavailable");
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        try (Polymarket sdk = Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), new ReadRetryPolicy(5, Duration.ZERO, Duration.ZERO),
                d -> {
                }), authority(), FIXED)) {
            sdk.trading().submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldSendExpirationAndPostOnlyWhenPlacementIsGtd() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}""");

        try (Polymarket sdk = sdk()) {
            sdk.trading().submit(signedOrder(),
                    OrderPlacement.goodTilDate(CREDENTIALS, 1_800_000_000L).asPostOnly());
        }

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"expiration\":\"1800000000\""), body);
        assertTrue(body.contains("\"postOnly\":true"), body);
    }

    @Test
    void shouldSendPostOnlyWhenIntentIsMakerOnly() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}""");

        try (Polymarket sdk = sdk()) {
            sdk.trading().place(OrderExecution.of(new MakerOnlyLimitOrder(new TokenId("123"),
                    Side.BUY, Price.of("0.52"), ShareQuantity.of("10")), RULES), context(),
                    CREDENTIALS);
        }

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"orderType\":\"GTC\""), body);
        assertTrue(body.contains("\"postOnly\":true"), body);
    }

    @Test
    void shouldDeriveExpirationWhenIntentIsGoodTilDate() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}""");

        try (Polymarket sdk = sdk()) {
            sdk.trading().place(OrderExecution.of(GoodTilDateOrder.expiringAt(new TokenId("123"),
                    Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                    Instant.ofEpochSecond(1_800_000_000L), FIXED), RULES), context(), CREDENTIALS);
        }

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"orderType\":\"GTD\""), body);
        // constraints.json gtd.securityThresholdSeconds = 60: 1800000000 + 60.
        assertTrue(body.contains("\"expiration\":\"1800000060\""), body);
    }

    @Test
    void shouldThrowWhenPlacementContradictsIntent() throws Exception {
        MakerOnlyLimitOrder intent = new MakerOnlyLimitOrder(
                new TokenId("123"), Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));
        SignedOrder order = signedOrder();

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading()
                    .submit(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC), intent));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenIntentIsOffGridOrUndersized() throws Exception {
        TokenId asset = new TokenId("123");

        assertThrows(IllegalArgumentException.class, () -> OrderExecution.of(
                new LimitOrder(asset, Side.BUY, Price.of("0.525"), ShareQuantity.of("10")), RULES));
        assertThrows(IllegalArgumentException.class, () -> OrderExecution.of(
                new LimitOrder(asset, Side.BUY, Price.of("0.52"), ShareQuantity.of("0.5")), RULES));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenGtdLifetimeIsTooShort() throws Exception {
        // The wire adds the 60-second threshold, so 119 effective seconds is still under 180.
        assertThrows(IllegalArgumentException.class, () -> GoodTilDateOrder.expiringAt(
                new TokenId("123"), Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                FIXED.instant().plusSeconds(119), FIXED));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenGtdPlacementIsTooNearExpiry() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}""");

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading().submit(signedOrder(),
                    OrderPlacement.goodTilDate(CREDENTIALS,
                            FIXED.instant().plusSeconds(179).getEpochSecond())));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenImmediateOrderIsPostOnly() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> OrderPlacement.of(CREDENTIALS, OrderType.FOK).asPostOnly());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenAmountExceedsDecimalPrecision() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading().sign(
                    new TokenId("123"), Side.BUY, Price.of("0.52"),
                    ShareQuantity.of("10.0000001"), RULES, context()));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldPreserveTransactionHashesWhenOrderIsMatched() throws Exception {
        // order-submission.json sendOrderResponse: the matched_order example, verbatim.
        enqueue(200, """
                {"success":true,"orderID":"0xabcdef1234567890abcdef1234567890abcdef12",
                 "status":"matched","makingAmount":"100000000","takingAmount":"200000000",
                 "transactionsHashes":["0x1234567890abcdef1234567890abcdef12345678"],
                 "tradeIDs":["trade-123"],"errorMsg":""}""");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Accepted accepted =
                assertInstanceOf(SubmissionOutcome.Accepted.class, outcome);
        assertEquals(java.util.List.of("0x1234567890abcdef1234567890abcdef12345678"),
                accepted.transactionHashes());
        assertEquals(java.util.List.of("trade-123"), accepted.tradeIds());
    }

    @Test
    void shouldLeaveTransactionHashesEmptyWhenOrderIsLive() throws Exception {
        // order-submission.json sendOrderResponse: the live_order example, verbatim.
        enqueue(200, """
                {"success":true,"orderID":"0xabcdef1234567890abcdef1234567890abcdef12",
                 "status":"live","makingAmount":"100000000","takingAmount":"200000000",
                 "errorMsg":""}""");

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        SubmissionOutcome.Accepted accepted =
                assertInstanceOf(SubmissionOutcome.Accepted.class, outcome);
        assertTrue(accepted.transactionHashes().isEmpty());
        assertTrue(accepted.tradeIds().isEmpty());
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenSuccessBodyIsNotAnOrder() throws Exception {
        for (String body : java.util.List.of("\"oops\"", "[]", "123", "null")) {
            enqueue(200, body);
            SubmissionOutcome outcome;
            try (Polymarket sdk = sdk()) {
                outcome = sdk.trading()
                        .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
            }
            assertInstanceOf(SubmissionOutcome.Unknown.class, outcome, body);
        }
    }

    @Test
    void shouldThrowWhenSignedOrderIsInvalid() {
        SignedOrder valid = signedOrder();

        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), "not-an-address",
                valid.signer(), valid.accountSigner(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "maker must be an address");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.accountSigner(), valid.asset(), valid.side(), valid.signatureType(),
                0L, valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "an order leg worth nothing is not an order");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(-1L, valid.maker(),
                valid.signer(), valid.accountSigner(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "salt is an unsigned field");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.accountSigner(), valid.asset(), valid.side(), 4,
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "4 is not an official signature type");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.accountSigner(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), "  "), "a blank signature authorises nothing");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.accountSigner(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), "0x0"), "a signature must contain complete bytes");

        assertEquals(0, server.getRequestCount(), "nothing may reach the wire");
    }

    @Test
    void shouldLeaveSubmissionUnknownWhenSuccessShapeIsUndocumented() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":12345,"status":"live","tradeIDs":[]}""");
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":7,"tradeIDs":[]}""");
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"invented","tradeIDs":[]}""");
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[123]}""");

        try (Polymarket sdk = sdk()) {
            SubmissionOutcome numericId = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
            SubmissionOutcome numericStatus = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
            SubmissionOutcome unknownStatus = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
            SubmissionOutcome malformedTrades = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));

            assertInstanceOf(SubmissionOutcome.Unknown.class, numericId,
                    "a numeric order id is not the documented string; it must not become Accepted");
            assertInstanceOf(SubmissionOutcome.Unknown.class, numericStatus);
            assertInstanceOf(SubmissionOutcome.Unknown.class, unknownStatus);
            assertInstanceOf(SubmissionOutcome.Unknown.class, malformedTrades);
        }
    }

    @Test
    void shouldAcceptWhenStatusIsUnmatched() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":"unmatched","tradeIDs":[]}""");

        try (Polymarket sdk = sdk()) {
            assertInstanceOf(SubmissionOutcome.Accepted.class, sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC)));
        }
    }

    @Test
    void shouldSubmitOnceWhenTransportFailsAfterRequest() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);

        SubmissionOutcome outcome;
        SignedOrder order = signedOrder();
        try (Polymarket sdk = Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), new ReadRetryPolicy(5, Duration.ZERO, Duration.ZERO),
                d -> {
                }), authority(), FIXED)) {
            outcome = sdk.trading().submit(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        assertInstanceOf(SubmissionOutcome.Unknown.class, outcome);
        assertEquals(1, server.getRequestCount());
    }
}
