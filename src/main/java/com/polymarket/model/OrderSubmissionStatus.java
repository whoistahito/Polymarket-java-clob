package com.polymarket.model;

/**
 * Disposition of a {@code POST /order} submission (Ticket 022).
 *
 * <p>Placement is not a boolean. The exchange gives no exactly-once guarantee, so the three cases
 * below are genuinely different and a caller that collapses them will either duplicate an order or
 * abandon a live one.
 */
public enum OrderSubmissionStatus {

    /**
     * The exchange returned a coherent success carrying a nonblank order ID and status. The order
     * exists; its fills must be read from reconciliation, never assumed from the request.
     */
    ACCEPTED,

    /**
     * The exchange definitively refused the order before it could rest or match. Nothing is live, so
     * the same order may be re-submitted (see {@link OrderSubmission#isSafeToRetry()} for whether the
     * documented error invites an immediate retry).
     */
    REJECTED,

    /**
     * The outcome is indeterminate: transport loss, a generic 5xx, a null body, or a contradictory /
     * malformed success. The order may or may not be live. Reconcile actual state before submitting
     * anything else for the same intent.
     */
    UNKNOWN
}
