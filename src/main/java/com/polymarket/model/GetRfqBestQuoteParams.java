package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/** Parameters for fetching the best RFQ quote. Mirrors TS {@code GetRfqBestQuoteParams}. */
@Value
@Builder
public class GetRfqBestQuoteParams {

    String requestId;
}
