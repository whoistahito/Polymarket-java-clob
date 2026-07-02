package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Payload for cancelling an RFQ quote. Mirrors TS {@code CancelRfqQuoteParams}. */
@Value
@Builder
@Jacksonized
public class CancelRfqQuoteParams {

    @JsonProperty("quoteId")
    String quoteId;
}
