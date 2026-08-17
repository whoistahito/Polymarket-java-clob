package com.polymarket.trading;

/**
 * Submits a signed V2 order exactly once. Never throws for a network or exchange failure —
 * every outcome, including transport loss, is a {@link SubmissionOutcome}.
 */
public interface OrderSubmitter {

    SubmissionOutcome submit(SignedOrder order, OrderPlacement placement);
}
