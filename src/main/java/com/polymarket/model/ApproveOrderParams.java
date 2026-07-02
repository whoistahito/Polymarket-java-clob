package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/** Parameters for approving an RFQ order (maker side). Mirrors TS {@code ApproveOrderParams}. */
@Value
@Builder
public class ApproveOrderParams {

    String requestId;
    String quoteId;
    long expiration;
}
