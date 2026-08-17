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
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.BatchItem;
import com.polymarket.trading.BatchSubmissionOutcome;
import com.polymarket.trading.CancellationOutcome;
import com.polymarket.trading.OrderPlacement;
import com.polymarket.trading.OrderType;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import com.polymarket.trading.Trading;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trading: atomic batches and typed cancellations (issue #17)")
class OrderBatchTest {

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
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, FIXED);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    private BatchItem item(String tokenId, long salt) {
        try (Polymarket sdk = sdk()) {
            SigningContext context = SigningContext.of(
                    SigningIdentity.eoa(SIGNER.address()), SIGNER, salt, FIXED.instant());
            SignedOrder order = sdk.trading().sign(new TokenId(tokenId), Side.BUY,
                    PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);
            return new BatchItem(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }
    }

    @Test
    @DisplayName("TC-BA-001: a batch over the official limit of 15 sends nothing")
    void oversizeBatchSendsNothing() throws Exception {
        List<BatchItem> items = new ArrayList<>();
        for (long i = 0; i < 16; i++) items.add(item("123", i + 1));

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading().submitBatch(items));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-002: a valid batch sends exactly one request and attaches per-item outcomes")
    void validBatchSendsOneRequestWithPerItemOutcomes() throws Exception {
        enqueue("""
                [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]},
                 {"success":false,"errorMsg":"invalid signature"}]""");

        BatchSubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        BatchSubmissionOutcome.Completed completed =
                assertInstanceOf(BatchSubmissionOutcome.Completed.class, outcome);
        assertEquals(2, completed.items().size());
        assertInstanceOf(com.polymarket.trading.SubmissionOutcome.Accepted.class, completed.items().get(0));
        assertInstanceOf(com.polymarket.trading.SubmissionOutcome.Rejected.class, completed.items().get(1));
        assertEquals(1, server.getRequestCount());

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/orders", request.getPath());
    }

    @Test
    @DisplayName("TC-BA-003: transport uncertainty never invents which item succeeded")
    void transportFailureDoesNotInventPerItemOutcomes() throws Exception {
        enqueue("not json at all");

        BatchSubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        assertInstanceOf(BatchSubmissionOutcome.Indeterminate.class, outcome);
    }

    @Test
    @DisplayName("TC-BA-004: a mismatched response array length is indeterminate, not partially attributed")
    void mismatchedResponseLengthIsIndeterminate() throws Exception {
        enqueue("""
                [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]}]""");

        BatchSubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        assertInstanceOf(BatchSubmissionOutcome.Indeterminate.class, outcome);
    }

    @Test
    @DisplayName("TC-BA-005: cancelling more than 1000 ids fails before sending")
    void oversizeCancelSendsNothing() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 1001; i++) ids.add("id-" + i);

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.trading().cancel(CREDENTIALS, SIGNER.address(), ids));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-006: blank and duplicate ids fail before sending")
    void blankAndDuplicateIdsRejected() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class, () -> sdk.trading()
                    .cancel(CREDENTIALS, SIGNER.address(), List.of("id-1", "")));
            assertThrows(IllegalArgumentException.class, () -> sdk.trading()
                    .cancel(CREDENTIALS, SIGNER.address(), List.of("id-1", "id-1")));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-007: cancellation distinguishes canceled from not-canceled ids")
    void cancellationDistinguishesOutcomePerId() throws Exception {
        enqueue("""
                {"canceled":["id-1"],"not_canceled":{"id-2":"order already matched"}}""");

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of("id-1", "id-2"));
        }

        assertTrue(outcome.isCanceled("id-1"));
        assertTrue(!outcome.isCanceled("id-2"));
        assertEquals("order already matched", outcome.notCanceled().get("id-2"));

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/orders", request.getPath());
        assertEquals("[\"id-1\",\"id-2\"]", request.getBody().readUtf8());
    }

    @Test
    @DisplayName("TC-BA-008: an id the server silently drops is still classified not-canceled")
    void silentlyDroppedIdIsNotCanceled() throws Exception {
        enqueue("""
                {"canceled":["id-1"]}""");

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of("id-1", "id-2"));
        }

        assertTrue(outcome.notCanceled().containsKey("id-2"));
    }

    @Test
    @DisplayName("TC-BA-009: the HMAC signature covers exactly the bytes sent as the cancel body")
    void hmacCoversExactBytesSent() throws Exception {
        enqueue("""
                {"canceled":["id-1"]}""");

        try (Polymarket sdk = sdk()) {
            sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of("id-1"));
        }

        RecordedRequest request = server.takeRequest();
        String sentBody = request.getBody().readUtf8();
        String expectedSignature = com.polymarket.internal.authentication.L2Attestation.headers(
                CREDENTIALS, SIGNER.address(), 1773890758L, "DELETE", "/orders", sentBody)
                .get("POLY_SIGNATURE");
        assertEquals(expectedSignature, request.getHeader("POLY_SIGNATURE"));
        // A single trailing-byte difference must not still verify: proves the signature is
        // actually bound to these bytes, not merely present.
        String tamperedSignature = com.polymarket.internal.authentication.L2Attestation.headers(
                CREDENTIALS, SIGNER.address(), 1773890758L, "DELETE", "/orders", sentBody + " ")
                .get("POLY_SIGNATURE");
        assertTrue(!tamperedSignature.equals(request.getHeader("POLY_SIGNATURE")));
    }

    @Test
    @DisplayName("TC-BA-010: a batch spanning two signers fails before sending")
    void mixedSignerBatchRejected() throws Exception {
        PrivateKeySigner otherSigner = PrivateKeySigner.of(
                "161bbf3b1117bf6f46dbc9cfef9cec88234d6120f06ba4f7a071a605aa7d40b3");
        SigningContext otherContext = SigningContext.of(
                SigningIdentity.eoa(otherSigner.address()), otherSigner, 9L, FIXED.instant());
        BatchItem first = item("123", 1);
        BatchItem second;
        try (Polymarket sdk = sdk()) {
            SignedOrder order = sdk.trading().sign(new TokenId("456"), Side.BUY,
                    PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, otherContext);
            second = new BatchItem(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.trading().submitBatch(List.of(first, second)));
        }
        assertEquals(0, server.getRequestCount());
    }
}
