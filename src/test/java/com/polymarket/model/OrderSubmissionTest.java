package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.client.HttpStatusException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 022 — order-submission disposition.
 *
 * <p>A successful {@code POST /order} requires a coherent success body carrying a nonblank order ID.
 * Transport loss, a generic 5xx, a null body, and a contradictory 2xx are NOT definitive rejections,
 * so callers must be able to tell {@code REJECTED} from {@code UNKNOWN} without parsing strings.
 */
@DisplayName("TC-OSD — order submission disposition (Ticket 022)")
class OrderSubmissionTest {

    private static OrderResponse ok(String orderId, String status) {
        return OrderResponse.builder()
            .success(true)
            .orderID(orderId)
            .status(status)
            .tradeIDs(List.of("t1"))
            .build();
    }

    // ------------------------------------------------------------------ //
    // ACCEPTED                                                            //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-OSD-001 valid matched FAK is ACCEPTED")
    void matchedFakAccepted() {
        OrderSubmission submission =
            OrderSubmission.fromResponse(ok("0xorder1", "matched"), 200, "{\"success\":true}");

        assertEquals(OrderSubmissionStatus.ACCEPTED, submission.status());
        assertTrue(submission.isAccepted());
        assertFalse(submission.isUnknown());
        assertEquals("0xorder1", submission.orderId());
        assertEquals(200, submission.httpStatus());
        assertNotNull(submission.response());
    }

    @Test
    @DisplayName("TC-OSD-002 ACCEPTED requires a nonblank status alongside the order ID")
    void acceptedRequiresStatus() {
        assertEquals(
            OrderSubmissionStatus.UNKNOWN,
            OrderSubmission.fromResponse(ok("0xorder1", "  "), 200, "{}").status());
        assertEquals(
            OrderSubmissionStatus.UNKNOWN,
            OrderSubmission.fromResponse(ok("0xorder1", null), 200, "{}").status());
    }

    // ------------------------------------------------------------------ //
    // REJECTED                                                            //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-OSD-003 no-match FAK 400 is REJECTED and not safe to retry")
    void noMatchFakRejected() {
        HttpStatusException error =
            new HttpStatusException(
                400,
                "{\"error\":\"no orders found to match with FAK order.\"}",
                "HTTP 400");
        OrderSubmission submission = OrderSubmission.fromFailure(error);

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertTrue(submission.isRejected());
        assertFalse(submission.isSafeToRetry());
        assertEquals(400, submission.httpStatus());
        assertTrue(submission.responseBody().contains("no orders found to match"));
    }

    @Test
    @DisplayName("TC-OSD-004 documented 'order timed out' 500 is REJECTED and safe to retry")
    void documentedTimeoutRejectedSafeToRetry() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(500, "{\"error\":\"order timed out\"}", "HTTP 500"));

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertTrue(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-005 documented 503 cancel-only block is REJECTED and safe to retry")
    void cancelOnlyRejectedSafeToRetry() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(
                    503,
                    "{\"error\":\"Trading is currently cancel-only. New orders are not accepted\"}",
                    "HTTP 503"));

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertTrue(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-006 any 4xx is REJECTED — the request never entered the book")
    void undocumentedClientErrorRejected() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(new HttpStatusException(422, "{\"error\":\"nope\"}", "HTTP 422"));

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertFalse(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-007 explicit success=false 2xx body is REJECTED")
    void explicitFailureBodyRejected() {
        OrderResponse response =
            OrderResponse.builder().success(false).errorMsg("not enough balance / allowance").build();

        OrderSubmission submission = OrderSubmission.fromResponse(response, 200, "{}");
        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertEquals("not enough balance / allowance", submission.errorMessage());
        assertFalse(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-008 documented retryable processing error keeps the safe-to-retry flag")
    void retryableProcessingErrorBody() {
        OrderResponse response =
            OrderResponse.builder()
                .success(false)
                .errorMsg("order match delayed due to market conditions")
                .build();

        OrderSubmission submission = OrderSubmission.fromResponse(response, 200, "{}");
        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertTrue(submission.isSafeToRetry());
    }

    // ------------------------------------------------------------------ //
    // UNKNOWN                                                             //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-OSD-009 transport loss is UNKNOWN, never a rejection")
    void transportLossUnknown() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(new SocketTimeoutException("connection reset"));

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertTrue(submission.isUnknown());
        assertFalse(submission.isSafeToRetry());
        assertEquals(-1, submission.httpStatus());
        assertNotNull(submission.failure());
        assertNull(submission.orderId());
    }

    @Test
    @DisplayName("TC-OSD-010 generic 500 is UNKNOWN — no exactly-once guarantee")
    void genericServerErrorUnknown() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(500, "{\"error\":\"internal error\"}", "HTTP 500"));

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertFalse(submission.isSafeToRetry());
        assertEquals(500, submission.httpStatus());
    }

    @Test
    @DisplayName("TC-OSD-011 a 502 with no body is UNKNOWN")
    void badGatewayUnknown() {
        assertEquals(
            OrderSubmissionStatus.UNKNOWN,
            OrderSubmission.fromFailure(new HttpStatusException(502, null, "HTTP 502")).status());
    }

    @Test
    @DisplayName("TC-OSD-012 null response body is UNKNOWN")
    void nullBodyUnknown() {
        OrderSubmission submission = OrderSubmission.fromResponse(null, 200, null);

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertNull(submission.response());
    }

    @Test
    @DisplayName("TC-OSD-013 success with a blank order ID is UNKNOWN")
    void blankOrderIdUnknown() {
        assertEquals(
            OrderSubmissionStatus.UNKNOWN,
            OrderSubmission.fromResponse(ok("   ", "matched"), 200, "{}").status());
        assertEquals(
            OrderSubmissionStatus.UNKNOWN,
            OrderSubmission.fromResponse(ok(null, "matched"), 200, "{}").status());
    }

    @Test
    @DisplayName("TC-OSD-014 success plus an error message is contradictory and UNKNOWN")
    void successPlusErrorUnknown() {
        OrderResponse response =
            OrderResponse.builder()
                .success(true)
                .orderID("0xorder1")
                .status("matched")
                .errorMsg("order timed out")
                .build();

        OrderSubmission submission = OrderSubmission.fromResponse(response, 200, "{}");
        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertFalse(submission.isSafeToRetry());
    }

    // ------------------------------------------------------------------ //
    // Evidence preservation                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-OSD-015 HTTP status and body survive classification")
    void preservesStatusAndBody() {
        HttpStatusException error =
            new HttpStatusException(400, "{\"error\":\"invalid expiration\"}", "HTTP 400");
        OrderSubmission submission = OrderSubmission.fromFailure(error);

        assertEquals(400, submission.httpStatus());
        assertEquals("{\"error\":\"invalid expiration\"}", submission.responseBody());
        assertSame(error, submission.failure());
    }

    @Test
    @DisplayName("TC-OSD-016 a wrapped HttpStatusException is unwrapped before classification")
    void unwrapsNestedStatusException() {
        IOException wrapped =
            new IOException(
                "post failed",
                new HttpStatusException(400, "{\"error\":\"Invalid order payload\"}", "HTTP 400"));

        OrderSubmission submission = OrderSubmission.fromFailure(wrapped);
        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertEquals(400, submission.httpStatus());
    }

    @Test
    @DisplayName("TC-OSD-017 a null failure cannot be classified as a rejection")
    void nullFailureUnknown() {
        assertEquals(OrderSubmissionStatus.UNKNOWN, OrderSubmission.fromFailure(null).status());
    }

    // ------------------------------------------------------------------ //
    // Duplicate-order reclassification (Ticket 035)                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-OSD-018 documented duplicate-order 400 is UNKNOWN, not REJECTED")
    void duplicateOrderIsUnknownNotRejected() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(
                    400, "{\"error\":\"order 0xabc is invalid. Duplicated.\"}", "HTTP 400"));

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertFalse(submission.isSafeToRetry());
        assertEquals(400, submission.httpStatus());
        assertTrue(submission.responseBody().contains("Duplicated"));
    }

    @Test
    @DisplayName("TC-OSD-019 the duplicate-order match is id-agnostic")
    void duplicateOrderMatchIsIdAgnostic() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(
                    400,
                    "{\"error\":\"order 0xdeadbeef00112233 is invalid. Duplicated.\"}",
                    "HTTP 400"));

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
    }

    @Test
    @DisplayName("TC-OSD-020 a no-match FOK/FAK 400 stays REJECTED — the reclass does not broaden")
    void noMatchFokFakStillRejectedAfterDuplicateReclass() {
        OrderSubmission fok =
            OrderSubmission.fromFailure(
                new HttpStatusException(
                    400, "{\"error\":\"no orders found to match with FOK order.\"}", "HTTP 400"));
        OrderSubmission fak =
            OrderSubmission.fromFailure(
                new HttpStatusException(
                    400, "{\"error\":\"no orders found to match with FAK order.\"}", "HTTP 400"));

        assertEquals(OrderSubmissionStatus.REJECTED, fok.status());
        assertFalse(fok.isSafeToRetry());
        assertEquals(OrderSubmissionStatus.REJECTED, fak.status());
        assertFalse(fak.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-021 'not enough balance / allowance' 400 stays REJECTED via fromFailure")
    void balanceAllowance400StillRejected() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(400, "{\"error\":\"not enough balance / allowance\"}", "HTTP 400"));

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertFalse(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-022 the documented 'order timed out' 500 is unaffected by the duplicate reclass")
    void orderTimedOutStillRejectedAndRetryableAfterDuplicateReclass() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(500, "{\"error\":\"order timed out\"}", "HTTP 500"));

        assertEquals(OrderSubmissionStatus.REJECTED, submission.status());
        assertTrue(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-023 a generic 500 remains UNKNOWN, unaffected by the duplicate reclass")
    void genericServerErrorStillUnknownAfterDuplicateReclass() {
        OrderSubmission submission =
            OrderSubmission.fromFailure(
                new HttpStatusException(500, "{\"error\":\"internal error\"}", "HTTP 500"));

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertFalse(submission.isSafeToRetry());
    }

    @Test
    @DisplayName("TC-OSD-024 a duplicate-order error delivered inside a 2xx success=false body is UNKNOWN")
    void duplicateOrderInsideSuccessFalseBodyIsUnknown() {
        OrderResponse response =
            OrderResponse.builder()
                .success(false)
                .errorMsg("order 0xabc is invalid. Duplicated.")
                .build();

        OrderSubmission submission = OrderSubmission.fromResponse(response, 200, "{}");

        assertEquals(OrderSubmissionStatus.UNKNOWN, submission.status());
        assertFalse(submission.isSafeToRetry());
    }
}
