package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = OpenOrder.OpenOrderBuilder.class)
public class OpenOrder {
    String id;
    OrderStatusType status;
    String owner;
    @JsonProperty("maker_address")
    String makerAddress;
    String market;
    @JsonProperty("asset_id")
    String assetId;
    String side;
    @JsonProperty("original_size")
    String originalSize;
    @JsonProperty("size_matched")
    String sizeMatched;
    String price;
    @JsonProperty("associate_trades")
    List<String> associateTrades;
    String outcome;
    @JsonProperty("created_at")
    Long createdAt;
    String expiration;
    @JsonProperty("order_type")
    String orderType;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class OpenOrderBuilder {}
}
