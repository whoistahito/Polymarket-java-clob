package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Last-trade-price update ({@code event_type: "last_trade_price"}).
 *
 * <p>Emitted after each trade execution.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LastTradePrice extends WsMessage {

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** Market condition ID. */
    private String market;

    /** Price of the last executed trade (string). */
    private String price;

    /** Side of the last trade ({@code "BUY"} or {@code "SELL"}; may be absent). */
    private String side;

    /** Size of the last trade (may be absent). */
    private String size;

    /** Fee rate in basis points (may be absent). */
    @JsonProperty("fee_rate_bps")
    private String feeRateBps;

    /** Unix timestamp in milliseconds (string on the wire). */
    private String timestamp;
}
