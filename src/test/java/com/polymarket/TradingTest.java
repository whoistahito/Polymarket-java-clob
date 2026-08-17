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
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
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

    private SignedOrder signedOrder() {
        SigningContext context = SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());
        try (Polymarket sdk = sdk()) {
            return sdk.trading().sign(new TokenId("123"), Side.BUY,
                    PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);
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
                    PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);
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
                    OrderPlacement.of(CREDENTIALS, OrderType.GTD).expiringAt(1_800_000_000L).asPostOnly());
        }

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"expiration\":\"1800000000\""), body);
        assertTrue(body.contains("\"postOnly\":true"), body);
    }
}
