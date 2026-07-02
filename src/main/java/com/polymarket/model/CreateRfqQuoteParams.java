package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Payload for {@code POST /rfq/quote}. Mirrors TS {@code CreateRfqQuoteParams}. */
@Value
@Builder
@Jacksonized
public class CreateRfqQuoteParams {

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("assetIn")
    String assetIn;

    @JsonProperty("assetOut")
    String assetOut;

    @JsonProperty("amountIn")
    String amountIn;

    @JsonProperty("amountOut")
    String amountOut;

    @JsonProperty("userType")
    int userType;
}
