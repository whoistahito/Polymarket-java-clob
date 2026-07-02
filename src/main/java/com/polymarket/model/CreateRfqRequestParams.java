package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Payload for {@code POST /rfq/request}. Mirrors TS {@code CreateRfqRequestParams}. */
@Value
@Builder
@Jacksonized
public class CreateRfqRequestParams {

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
