package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/** Parameters for accepting an RFQ quote (taker side). Mirrors TS {@code AcceptQuoteParams}. */
@Value
@Builder
public class AcceptQuoteParams {

    String requestId;
    String quoteId;
    long expiration;
}
