package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import java.util.List;

/** Batch submission and cancellation. Each call is exactly one wire request, never chunked. */
public interface OrderBatch {

    BatchSubmissionOutcome submitBatch(List<BatchItem> items);

    /** Never throws for a network or exchange failure: uncertainty is a CancellationOutcome. */
    CancellationOutcome cancel(ApiCredentials credentials, String address, List<String> orderIds);
}
