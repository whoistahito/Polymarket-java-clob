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
import com.polymarket.markets.Price;
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
import okhttp3.mockwebserver.SocketPolicy;
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
    /** Official order-hash examples: manage-orders.md OpenOrder.id and the order_id query examples. */
    private static final String ID_1 =
            "0xff354cd7ca7539dfa9c28d90943ab5779a4eac34b9b37a757d7b32bdfb11790b";
    private static final String ID_2 =
            "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String ID_3 =
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

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
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
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
        for (int i = 0; i < 1001; i++) ids.add(String.format("0x%064x", i));

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
                    .cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1, "")));
            assertThrows(IllegalArgumentException.class, () -> sdk.trading()
                    .cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1, ID_1)));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-007: cancellation distinguishes canceled from not-canceled ids")
    void cancellationDistinguishesOutcomePerId() throws Exception {
        enqueue("{\"canceled\":[\"" + ID_1 + "\"],\"not_canceled\":{\""
                + ID_2 + "\":\"order already matched\"}}");

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1, ID_2));
        }

        CancellationOutcome.Completed completed =
                assertInstanceOf(CancellationOutcome.Completed.class, outcome);
        assertTrue(completed.isCanceled(ID_1));
        assertTrue(!completed.isCanceled(ID_2));
        assertEquals("order already matched", completed.notCanceled().get(ID_2));

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/orders", request.getPath());
        assertEquals("[\"" + ID_1 + "\",\"" + ID_2 + "\"]", request.getBody().readUtf8());
    }

    @Test
    @DisplayName("TC-BA-008: an id the server never mentions is unaccounted, not a stated refusal")
    void silentlyDroppedIdIsUnaccountedNotRefused() throws Exception {
        enqueue("{\"canceled\":[\"" + ID_1 + "\"],\"not_canceled\":{\""
                + ID_2 + "\":\"order already matched\"}}");

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading()
                    .cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1, ID_2, ID_3));
        }

        CancellationOutcome.Completed completed =
                assertInstanceOf(CancellationOutcome.Completed.class, outcome);
        assertEquals(List.of(ID_3), completed.unaccounted());
        assertTrue(!completed.notCanceled().containsKey(ID_3));
        assertTrue(!completed.isCanceled(ID_3));
    }

    @Test
    @DisplayName("TC-BA-009: the HMAC signature covers exactly the bytes sent as the cancel body")
    void hmacCoversExactBytesSent() throws Exception {
        enqueue("{\"canceled\":[\"" + ID_1 + "\"],\"not_canceled\":{}}");

        try (Polymarket sdk = sdk()) {
            sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1));
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
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, otherContext);
            second = new BatchItem(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC));
        }

        try (Polymarket sdk = sdk()) {
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.trading().submitBatch(List.of(first, second)));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-011: an equal-length batch response holding a malformed element is indeterminate")
    void malformedBatchElementMakesTheWholeBatchIndeterminate() throws Exception {
        enqueue("""
                [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]},
                 "not an order object"]""");

        BatchSubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        assertInstanceOf(BatchSubmissionOutcome.Indeterminate.class, outcome);
    }

    @Test
    @DisplayName("TC-BA-012: a batch element missing a required field is indeterminate, not a rejection")
    void batchElementWithoutRequiredSuccessIsIndeterminate() throws Exception {
        // clob-openapi.yaml SendOrderResponse requires success, orderID and status.
        enqueue("""
                [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]},
                 {"orderID":"0xb","status":"live"}]""");

        BatchSubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        assertInstanceOf(BatchSubmissionOutcome.Indeterminate.class, outcome);
    }

    @Test
    @DisplayName("TC-BA-021: a successful batch element whose id is not text makes the batch indeterminate")
    void batchElementWithANonTextualIdIsIndeterminate() throws Exception {
        // Structurally an order object, but orderID and status are documented as strings. A number
        // is not the id the server meant, and a batch cannot be half-attributed.
        for (String malformed : List.of(
                "{\"success\":true,\"orderID\":456,\"status\":\"live\",\"tradeIDs\":[]}",
                "{\"success\":true,\"orderID\":\"0xb\",\"status\":7,\"tradeIDs\":[]}")) {
            enqueue("""
                    [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]},
                     """ + malformed + "]");

            BatchSubmissionOutcome outcome;
            try (Polymarket sdk = sdk()) {
                outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
            }

            assertInstanceOf(BatchSubmissionOutcome.Indeterminate.class, outcome, malformed);
        }
    }

    @Test
    @DisplayName("TC-BA-023: an undocumented successful status makes the batch indeterminate")
    void undocumentedBatchStatusIsIndeterminate() throws Exception {
        enqueue("""
                [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]},
                 {"success":true,"orderID":"0xb","status":"invented","tradeIDs":[]}]""");

        BatchSubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        assertInstanceOf(BatchSubmissionOutcome.Indeterminate.class, outcome);
    }

    @Test
    @DisplayName("TC-BA-022: a cancellation member that is not text is uncertain, never coerced")
    void malformedCancellationMembersAreUncertain() throws Exception {
        for (String body : List.of(
                "{\"canceled\":[123],\"not_canceled\":{}}",
                "{\"canceled\":[{\"id\":\"" + ID_1 + "\"}],\"not_canceled\":{}}",
                "{\"canceled\":[],\"not_canceled\":{\"" + ID_1 + "\":404}}")) {
            enqueue(body);
            CancellationOutcome outcome;
            try (Polymarket sdk = sdk()) {
                outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1));
            }
            assertInstanceOf(CancellationOutcome.Uncertain.class, outcome, body);
        }
    }

    @Test
    @DisplayName("TC-BA-013: cancellation transport loss is an explicit uncertain outcome, never thrown")
    void cancellationTransportLossIsUncertain() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1));
        }

        CancellationOutcome.Uncertain uncertain =
                assertInstanceOf(CancellationOutcome.Uncertain.class, outcome);
        assertTrue(uncertain.cause().isPresent());
    }

    @Test
    @DisplayName("TC-BA-014: a non-success cancellation status is uncertain, never thrown")
    void nonSuccessCancellationStatusIsUncertain() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"Trading is currently disabled\"}"));

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1));
        }

        CancellationOutcome.Uncertain uncertain =
                assertInstanceOf(CancellationOutcome.Uncertain.class, outcome);
        assertEquals(503, uncertain.httpStatus().orElseThrow());
    }

    @Test
    @DisplayName("TC-BA-015: a malformed cancellation success body is uncertain, not an empty result")
    void malformedCancellationSuccessIsUncertain() throws Exception {
        // clob-openapi.yaml CancelOrdersResponse requires canceled and not_canceled.
        for (String body : List.of("not json at all", "[]", "{}", "{\"canceled\":\"" + ID_1 + "\"}")) {
            enqueue(body);
            CancellationOutcome outcome;
            try (Polymarket sdk = sdk()) {
                outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1));
            }
            assertInstanceOf(CancellationOutcome.Uncertain.class, outcome, body);
        }
    }

    @Test
    @DisplayName("TC-BA-016: definitive canceled and not-canceled identifiers stay in separate sets")
    void canceledAndNotCanceledStayDistinct() throws Exception {
        enqueue("{\"canceled\":[\"" + ID_1 + "\"],\"not_canceled\":{\""
                + ID_2 + "\":\"Order not found\"}}");

        CancellationOutcome outcome;
        try (Polymarket sdk = sdk()) {
            outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1, ID_2));
        }

        CancellationOutcome.Completed completed =
                assertInstanceOf(CancellationOutcome.Completed.class, outcome);
        assertEquals(List.of(ID_1), completed.canceled());
        assertEquals(java.util.Map.of(ID_2, "Order not found"), completed.notCanceled());
        assertTrue(completed.unaccounted().isEmpty());
    }

    @Test
    @DisplayName("TC-BA-024: contradictory or unrelated cancellation facts are uncertain")
    void contradictoryCancellationFactsAreUncertain() throws Exception {
        for (String body : List.of(
                "{\"canceled\":[\"" + ID_1 + "\"],\"not_canceled\":{\""
                        + ID_1 + "\":\"not found\"}}",
                "{\"canceled\":[\"" + ID_2 + "\"],\"not_canceled\":{}}")) {
            enqueue(body);
            CancellationOutcome outcome;
            try (Polymarket sdk = sdk()) {
                outcome = sdk.trading().cancel(CREDENTIALS, SIGNER.address(), List.of(ID_1));
            }
            assertInstanceOf(CancellationOutcome.Uncertain.class, outcome, body);
        }
    }

    @Test
    @DisplayName("TC-BA-017: an order id outside the documented 0x-hex shape fails before sending")
    void malformedOrderIdSendsNothing() throws Exception {
        // order-submission.json orderIdentifierSyntax: every official example is 0x-hex; only the
        // length disagrees between sources, so the shape is enforced and the length is not.
        try (Polymarket sdk = sdk()) {
            for (String malformed : List.of("id-1", "0x", "ff354cd7", "0xzz", "0x1234 ")) {
                assertThrows(IllegalArgumentException.class,
                        () -> sdk.trading().cancel(CREDENTIALS, SIGNER.address(),
                                List.of(malformed)), malformed);
            }
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-018: both documented order-id lengths are accepted, since the sources disagree")
    void bothDocumentedOrderIdLengthsAreAccepted() throws Exception {
        enqueue("""
                {"canceled":[],"not_canceled":{}}""");

        // clob-openapi.yaml examples: 0x + 40 hex (CancelOrderPayload) and 0x + 64 hex (order_id).
        try (Polymarket sdk = sdk()) {
            assertInstanceOf(CancellationOutcome.Completed.class, sdk.trading()
                    .cancel(CREDENTIALS, SIGNER.address(),
                            List.of("0xabcdef1234567890abcdef1234567890abcdef12", ID_1)));
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BA-019: the HMAC signature covers exactly the bytes sent as the batch body")
    void batchHmacCoversExactBytesSent() throws Exception {
        enqueue("""
                [{"success":true,"orderID":"0xa","status":"live","tradeIDs":[]},
                 {"success":true,"orderID":"0xb","status":"live","tradeIDs":[]}]""");

        try (Polymarket sdk = sdk()) {
            sdk.trading().submitBatch(List.of(item("123", 1), item("456", 2)));
        }

        assertSignatureBindsBody(server.takeRequest(), "POST", "/orders");
    }

    @Test
    @DisplayName("TC-BA-020: the HMAC signature covers exactly the bytes sent as the order body")
    void singleOrderHmacCoversExactBytesSent() throws Exception {
        enqueue("""
                {"success":true,"orderID":"0xa","status":"live","tradeIDs":[]}""");

        BatchItem single = item("123", 1);
        try (Polymarket sdk = sdk()) {
            sdk.trading().submit(single.order(), single.placement());
        }

        assertSignatureBindsBody(server.takeRequest(), "POST", "/order");
    }

    /** Re-signs the bytes actually on the wire, then proves one extra byte no longer verifies. */
    private static void assertSignatureBindsBody(RecordedRequest request, String method, String path) {
        String sentBody = request.getBody().readUtf8();
        assertEquals(path, request.getPath());
        assertEquals(com.polymarket.internal.authentication.L2Attestation.headers(
                        CREDENTIALS, SIGNER.address(), 1773890758L, method, path, sentBody)
                .get("POLY_SIGNATURE"), request.getHeader("POLY_SIGNATURE"));
        assertTrue(!com.polymarket.internal.authentication.L2Attestation.headers(
                        CREDENTIALS, SIGNER.address(), 1773890758L, method, path, sentBody + " ")
                .get("POLY_SIGNATURE").equals(request.getHeader("POLY_SIGNATURE")));
    }
}
