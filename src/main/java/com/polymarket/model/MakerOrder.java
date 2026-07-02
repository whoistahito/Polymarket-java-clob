package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = MakerOrder.MakerOrderBuilder.class)
public class MakerOrder {
    @JsonProperty("order_id")
    String orderId;
    String owner;
    @JsonProperty("maker_address")
    String makerAddress;
    @JsonProperty("matched_amount")
    String matchedAmount;
    String price;
    @JsonProperty("fee_rate_bps")
    String feeRateBps;
    @JsonProperty("asset_id")
    String assetId;
    String outcome;
    Side side;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class MakerOrderBuilder {}
}
