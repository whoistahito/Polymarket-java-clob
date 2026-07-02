package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Best-bid/ask snapshot ({@code event_type: "best_bid_ask"}).
 *
 * <p>Requires {@code custom_feature_enabled: true} on the subscription request.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BestBidAsk extends WsMessage {

    /** Market condition ID. */
    private String market;

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** Current best bid price (string). */
    @JsonProperty("best_bid")
    private String bestBid;

    /** Current best ask price (string). */
    @JsonProperty("best_ask")
    private String bestAsk;

    /** Spread between best bid and ask (string). */
    private String spread;

    /** Unix timestamp in milliseconds (string on the wire). */
    private String timestamp;
}
