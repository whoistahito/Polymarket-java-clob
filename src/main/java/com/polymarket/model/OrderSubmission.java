package com.polymarket.model;

import com.polymarket.client.HttpStatusException;
import java.util.List;
import java.util.Locale;

/**
 * Typed disposition of one order submission (Ticket 022).
 *
 * <p>Classifies the outcome of {@code POST /order} into {@link OrderSubmissionStatus#ACCEPTED},
 * {@link OrderSubmissionStatus#REJECTED}, or {@link OrderSubmissionStatus#UNKNOWN} so callers can
 * branch without parsing error strings or dereferencing nullable fields.
 *
 * <p>Classification rules, from the official docs
 * ({@code api-reference/trade/post-a-new-order.md}, {@code resources/error-codes.md}):
 *
 * <ul>
 *   <li><b>ACCEPTED</b> — a 2xx body with {@code success=true}, a nonblank {@code orderID}, a
 *       nonblank {@code status}, and no error message.</li>
 *   <li><b>REJECTED</b> — any 4xx other than the documented duplicate-order error (the request
 *       never entered the book), a documented 500 {@code "order timed out"}, a documented 503
 *       service block, or a 2xx body that explicitly reports {@code success=false}. Definitively
 *       not live.</li>
 *   <li><b>UNKNOWN</b> — transport loss, a generic 5xx, a null body, a success without an order ID,
 *       a success that also carries an error message, or the documented duplicate-order 400
 *       ({@code "order {id} is invalid. Duplicated."}) — a duplicate proves an earlier attempt
 *       already reached the book, so it is not evidence that nothing is live (Ticket 035). May or
 *       may not be live.</li>
 * </ul>
 *
 * <p>The HTTP status and raw response body are preserved on every disposition so a caller can log
 * exactly what the exchange said.
 */
public record OrderSubmission(
    OrderSubmissionStatus status,
    OrderResponse response,
    int httpStatus,
    String responseBody,
    String errorMessage,
    boolean safeToRetry,
    Throwable failure) {

    /** Sentinel {@link #httpStatus()} for a submission that never produced an HTTP response. */
    public static final int NO_HTTP_STATUS = -1;

    /**
     * Documented errors that mean "not placed, try again" rather than "not placed, stop".
     *
     * <p>Matched case-insensitively as substrings because the API interpolates ids/addresses into
     * several of these messages.
     */
    private static final List<String> RETRYABLE_ERRORS = List.of(
        "order timed out",
        "order match delayed due to market conditions",
        "the market is not yet ready to process new orders",
        "trading is currently cancel-only",
        "post-only mode: only post-only orders and cancels are allowed");

    /**
     * Documented order-processing duplicate error, HTTP 400: {@code "order {id} is invalid.
     * Duplicated."} (Ticket 035). Matched without the interpolated order id, and NOT added to
     * {@link #RETRYABLE_ERRORS} — a duplicate is not itself safe to retry, it is merely not proof of
     * a definitive rejection.
     */
    private static final String DUPLICATE_ORDER_MARKER = "is invalid. duplicated";

    // --------------------------------------------------------------------- //
    // Factories                                                              //
    // --------------------------------------------------------------------- //

    /**
     * Classify a parsed response body returned with a 2xx status.
     *
     * @param response the deserialized body, or {@code null} when the body was absent/unparseable
     * @param httpStatus the HTTP status code that carried it
     * @param responseBody the raw body text, preserved for logging (may be {@code null})
     */
    public static OrderSubmission fromResponse(
        OrderResponse response, int httpStatus, String responseBody) {

        if (response == null) {
            // A 2xx with no readable body proves nothing about whether the order rested.
            return new OrderSubmission(
                OrderSubmissionStatus.UNKNOWN, null, httpStatus, responseBody,
                "empty order response body", false, null);
        }

        String errorMsg = blankToNull(response.errorMsg());

        if (!response.success()) {
            if (isDuplicateOrderError(errorMsg)) {
                // A duplicate proves an earlier attempt already reached the book (Ticket 035): this
                // is not evidence that nothing is live, so it cannot be a definitive rejection.
                return new OrderSubmission(
                    OrderSubmissionStatus.UNKNOWN, response, httpStatus, responseBody,
                    errorMsg, false, null);
            }
            // An explicit failure is definitive: the exchange processed the request and refused it.
            return new OrderSubmission(
                OrderSubmissionStatus.REJECTED, response, httpStatus, responseBody,
                errorMsg, isRetryableError(errorMsg), null);
        }

        if (errorMsg != null) {
            // success AND an error message contradict each other; the real disposition is unreadable.
            return new OrderSubmission(
                OrderSubmissionStatus.UNKNOWN, response, httpStatus, responseBody,
                errorMsg, false, null);
        }
        if (blankToNull(response.orderID()) == null) {
            return new OrderSubmission(
                OrderSubmissionStatus.UNKNOWN, response, httpStatus, responseBody,
                "success without an order id", false, null);
        }
        if (blankToNull(response.status()) == null) {
            return new OrderSubmission(
                OrderSubmissionStatus.UNKNOWN, response, httpStatus, responseBody,
                "success without an order status", false, null);
        }

        return new OrderSubmission(
            OrderSubmissionStatus.ACCEPTED, response, httpStatus, responseBody, null, false, null);
    }

    /**
     * Classify a failed submission.
     *
     * @param error the thrown failure; an {@link HttpStatusException} anywhere in the cause chain
     *     supplies the status and body, anything else is treated as transport loss
     */
    public static OrderSubmission fromFailure(Throwable error) {
        HttpStatusException status = unwrapStatus(error);
        if (status == null) {
            // Transport loss (or a null failure): the request may have reached the matching engine.
            return new OrderSubmission(
                OrderSubmissionStatus.UNKNOWN, null, NO_HTTP_STATUS, null,
                error == null ? "unknown submission failure" : error.toString(), false, error);
        }

        int code = status.statusCode();
        String body = status.responseBody();
        String message = body != null && !body.isBlank() ? body : status.getMessage();

        if (code >= 400 && code < 500) {
            if (isDuplicateOrderError(message)) {
                // Documented duplicate-order error (Ticket 035), HTTP 400, "order {id} is invalid.
                // Duplicated." A duplicate proves an earlier attempt already reached the book, so
                // this 4xx is NOT evidence that nothing is live. Do not broaden this to other 4xx —
                // every other 4xx in this branch is still a definitive rejection.
                return new OrderSubmission(
                    OrderSubmissionStatus.UNKNOWN, null, code, body, message, false, error);
            }
            // 4xx means the request was refused before acceptance — nothing is resting.
            return new OrderSubmission(
                OrderSubmissionStatus.REJECTED, null, code, body, message,
                isRetryableError(message), error);
        }
        if (code >= 500 && isRetryableError(message)) {
            // Documented server-side refusals ("order timed out", cancel-only / post-only blocks)
            // state that the order was NOT placed, so they are definitive and safe to retry.
            return new OrderSubmission(
                OrderSubmissionStatus.REJECTED, null, code, body, message, true, error);
        }
        // Every other 5xx is indeterminate: the order may have been matched before the failure.
        return new OrderSubmission(
            OrderSubmissionStatus.UNKNOWN, null, code, body, message, false, error);
    }

    // --------------------------------------------------------------------- //
    // Convenience accessors                                                  //
    // --------------------------------------------------------------------- //

    public boolean isAccepted() {
        return status == OrderSubmissionStatus.ACCEPTED;
    }

    public boolean isRejected() {
        return status == OrderSubmissionStatus.REJECTED;
    }

    public boolean isUnknown() {
        return status == OrderSubmissionStatus.UNKNOWN;
    }

    /**
     * True only when the exchange definitively refused the order with a documented error that states
     * it was not placed. Never true for {@link OrderSubmissionStatus#UNKNOWN}.
     */
    public boolean isSafeToRetry() {
        return safeToRetry;
    }

    /** The accepted order's ID, or {@code null} when this submission produced none. */
    public String orderId() {
        return response == null ? null : blankToNull(response.orderID());
    }

    // --------------------------------------------------------------------- //
    // Internals                                                              //
    // --------------------------------------------------------------------- //

    private static HttpStatusException unwrapStatus(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpStatusException statusException
                && statusException.statusCode() > 0) {
                return statusException;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private static boolean isRetryableError(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return RETRYABLE_ERRORS.stream().anyMatch(normalized::contains);
    }

    private static boolean isDuplicateOrderError(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains(DUPLICATE_ORDER_MARKER);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
