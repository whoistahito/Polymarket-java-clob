package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single entry within a {@link PriceChange} batch. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceChangeBatchEntry {

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** New price (string to preserve precision). */
    private String price;

    /** Total size affected by this change (may be absent). */
    private String size;

    /** Side of the price change ({@code "BUY"} or {@code "SELL"}). */
    private String side;

    /** Validation hash (may be absent). */
    private String hash;

    /** Best bid price after this change (may be absent). */
    @JsonProperty("best_bid")
    private String bestBid;

    /** Best ask price after this change (may be absent). */
    @JsonProperty("best_ask")
    private String bestAsk;
}
