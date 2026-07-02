package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Payload for cancelling an RFQ request. Mirrors TS {@code CancelRfqRequestParams}. */
@Value
@Builder
@Jacksonized
public class CancelRfqRequestParams {

    @JsonProperty("requestId")
    String requestId;
}
