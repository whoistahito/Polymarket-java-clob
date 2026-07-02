package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = OrderBookSummary.OrderBookSummaryBuilder.class)
public class OrderBookSummary {
    String market;
    @JsonProperty("asset_id")
    String assetId;
    String timestamp;
    List<OrderSummary> bids;
    List<OrderSummary> asks;
    @JsonProperty("min_order_size")
    String minOrderSize;
    @JsonProperty("tick_size")
    String tickSize;
    @JsonProperty("neg_risk")
    Boolean negRisk;
    @JsonProperty("last_trade_price")
    String lastTradePrice;
    String hash;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class OrderBookSummaryBuilder {}
}
