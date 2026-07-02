package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = Trade.TradeBuilder.class)
public class Trade {
    String id;
    @JsonProperty("taker_order_id")
    String takerOrderId;
    String market;
    @JsonProperty("asset_id")
    String assetId;
    Side side;
    String size;
    @JsonProperty("fee_rate_bps")
    String feeRateBps;
    String price;
    TradeStatusType status;
    @JsonProperty("match_time")
    String matchTime;
    @JsonProperty("last_update")
    String lastUpdate;
    String outcome;
    @JsonProperty("bucket_index")
    Integer bucketIndex;
    String owner;
    @JsonProperty("maker_address")
    String makerAddress;
    @JsonProperty("maker_orders")
    List<MakerOrder> makerOrders;
    @JsonProperty("transaction_hash")
    String transactionHash;
    @JsonProperty("trader_side")
    TraderSide traderSide;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class TradeBuilder {}
}
