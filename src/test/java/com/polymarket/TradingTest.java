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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trading: classified order placement (issue #14)")
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
    @DisplayName("TC-TR-001: a coherent success is accepted with order id, status and trade ids")
    void coherentSuccessIsAccepted() throws Exception {
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
    @DisplayName("TC-TR-002: an explicit success=false is a definitive rejection")
    void explicitFailureIsRejected() throws Exception {
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
    @DisplayName("TC-TR-003: the documented 500 'order timed out' is a rejection safe to retry")
    void documentedServerTimeoutIsRejectedAndRetryable() throws Exception {
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
    @DisplayName("TC-TR-004: a generic 5xx is unknown, not a rejection")
    void genericServerFailureIsUnknown() throws Exception {
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
    @DisplayName("TC-TR-005: a documented 503 service block is a definitive rejection")
    void documentedServiceBlockIsRejected() throws Exception {
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
    @DisplayName("TC-TR-006: the documented duplicate-order 400 is unknown, not a rejection")
    void duplicateOrderErrorIsUnknown() throws Exception {
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
    @DisplayName("TC-TR-007: connection loss is unknown, never thrown")
    void connectionLossIsUnknown() throws Exception {
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
    @DisplayName("TC-TR-008: success without an order id is unknown, not accepted")
    void malformedSuccessIsUnknown() throws Exception {
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
    @DisplayName("TC-TR-009: success carrying an error message is unknown: contradictory")
    void contradictorySuccessIsUnknown() throws Exception {
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
    @DisplayName("TC-TR-010: a V3 Combo order is rejected before anything is sent")
    void positionIdOrderNeverReachesPostOrder() throws Exception {
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
    @DisplayName("TC-TR-011: submission is never replayed even with read retries configured")
    void submissionIsNeverReplayedDespiteReadRetries() throws Exception {
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
    @DisplayName("TC-TR-012: a GTD order carries its expiration and postOnly on the wire")
    void gtdOrderCarriesExpirationAndPostOnly() throws Exception {
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
    @DisplayName("TC-TR-013: a Maker-Only Order Intent places a post-only GTC order it never restates")
    void makerOnlyIntentRidesAlongToTheWire() throws Exception {
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
    @DisplayName("TC-TR-014: a GTD Order Intent derives its shifted expiration onto the wire")
    void goodTilDateIntentRidesAlongToTheWire() throws Exception {
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
    @DisplayName("TC-TR-015: a placement contradicting its Order Intent sends nothing")
    void contradictoryPlacementSendsNothing() throws Exception {
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
    @DisplayName("TC-TR-016: an Order Intent invalid against its Market Rule Snapshot sends nothing")
    void offGridAndUndersizedIntentsSendNothing() throws Exception {
        TokenId asset = new TokenId("123");

        assertThrows(IllegalArgumentException.class, () -> OrderExecution.of(
                new LimitOrder(asset, Side.BUY, Price.of("0.525"), ShareQuantity.of("10")), RULES));
        assertThrows(IllegalArgumentException.class, () -> OrderExecution.of(
                new LimitOrder(asset, Side.BUY, Price.of("0.52"), ShareQuantity.of("0.5")), RULES));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-TR-017: a GTD expiration inside the official minimum lifetime sends nothing")
    void tooSoonGoodTilDateSendsNothing() throws Exception {
        // constraints.json gtd.minimumFutureSeconds = 180.
        assertThrows(IllegalArgumentException.class, () -> GoodTilDateOrder.expiringAt(
                new TokenId("123"), Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                FIXED.instant().plusSeconds(179), FIXED));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-TR-018: a post-only attribute on an immediate order type sends nothing")
    void postOnlyImmediateOrderSendsNothing() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> OrderPlacement.of(CREDENTIALS, OrderType.FOK).asPostOnly());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-TR-019: an amount beyond the 6-decimal pUSD grid sends nothing")
    void unrepresentableAmountSendsNothing() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading().sign(
                    new TokenId("123"), Side.BUY, Price.of("0.52"),
                    ShareQuantity.of("10.0000001"), RULES, context()));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-TR-020: an accepted matched order preserves the documented transaction hashes")
    void acceptedPreservesDocumentedTransactionHashes() throws Exception {
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
    @DisplayName("TC-TR-021: an accepted live order carries no transaction hashes rather than a guess")
    void acceptedWithoutTransactionHashesIsEmpty() throws Exception {
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
    @DisplayName("TC-TR-022: a successful response that is not an order object is unknown, never rejected")
    void structurallyInvalidSuccessBodyIsUnknown() throws Exception {
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
    @DisplayName("TC-TR-024: a hand-built Signed Order that could not have been signed sends nothing")
    void anInvalidSignedOrderNeverReachesTheWire() {
        SignedOrder valid = signedOrder();

        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), "not-an-address",
                valid.signer(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "maker must be an address");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.asset(), valid.side(), valid.signatureType(),
                0L, valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "an order leg worth nothing is not an order");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(-1L, valid.maker(),
                valid.signer(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "salt is an unsigned field");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.asset(), valid.side(), 4,
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), valid.signature()), "4 is not an official signature type");
        assertThrows(IllegalArgumentException.class, () -> new SignedOrder(valid.salt(), valid.maker(),
                valid.signer(), valid.asset(), valid.side(), valid.signatureType(),
                valid.makerAmount(), valid.takerAmount(), valid.timestamp(), valid.metadata(),
                valid.builder(), "  "), "a blank signature authorises nothing");

        assertEquals(0, server.getRequestCount(), "nothing may reach the wire");
    }

    @Test
    @DisplayName("TC-TR-025: a success whose order id or status is not text states nothing")
    void nonTextualOrderIdOrStatusIsUnknown() throws Exception {
        enqueue(200, """
                {"success":true,"orderID":12345,"status":"live","tradeIDs":[]}""");
        enqueue(200, """
                {"success":true,"orderID":"0xabc","status":7,"tradeIDs":[]}""");

        try (Polymarket sdk = sdk()) {
            SubmissionOutcome numericId = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));
            SubmissionOutcome numericStatus = sdk.trading()
                    .submit(signedOrder(), OrderPlacement.of(CREDENTIALS, OrderType.GTC));

            assertInstanceOf(SubmissionOutcome.Unknown.class, numericId,
                    "a numeric order id is not the documented string; it must not become Accepted");
            assertInstanceOf(SubmissionOutcome.Unknown.class, numericStatus);
        }
    }

    @Test
    @DisplayName("TC-TR-023: transport loss after the order is on the wire is sent exactly once")
    void transportLossStillSubmitsExactlyOnce() throws Exception {
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
