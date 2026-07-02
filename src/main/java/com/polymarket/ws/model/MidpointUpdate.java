package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Derived midpoint update.
 *
 * <p>Not a wire message — produced by the client from a {@link BookUpdate} when
 * both a best bid and a best ask are present.
 * Mirrors the Rust {@code MidpointUpdate} struct.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidpointUpdate extends WsMessage {

    @JsonProperty("asset_id")
    private String assetId;

    private String market;

    /** Calculated mid-price: {@code (bestBid + bestAsk) / 2}. */
    private String midpoint;

    private String timestamp;
}
