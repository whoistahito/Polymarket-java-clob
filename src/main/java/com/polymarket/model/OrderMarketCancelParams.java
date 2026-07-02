package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/** Parameters for cancelling all orders in a market. */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderMarketCancelParams {
    String market;
    @JsonProperty("asset_id")
    String assetId;
}
