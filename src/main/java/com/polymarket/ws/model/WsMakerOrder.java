package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Maker-order details nested inside a {@link TradeMessage}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WsMakerOrder {

    @JsonProperty("asset_id")
    private String assetId;

    @JsonProperty("matched_amount")
    private String matchedAmount;

    @JsonProperty("order_id")
    private String orderId;

    private String outcome;

    /** Side of this maker order. Top-level trade side is always the taker's side. */
    private String side;

    /** API key of the maker. */
    private String owner;

    @JsonProperty("maker_address")
    private String makerAddress;

    private String price;

    @JsonProperty("fee_rate_bps")
    private String feeRateBps;
}
