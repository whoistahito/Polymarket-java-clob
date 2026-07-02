package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * User order-update message ({@code event_type: "order"}).
 *
 * <p>Only received on the authenticated user channel. Covers placement, partial
 * fills, and cancellations.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderMessage extends WsMessage {

    /** Order identifier. */
    private String id;

    /** Market condition ID. */
    private String market;

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** Side ({@code "BUY"} or {@code "SELL"}). */
    private String side;

    /** Order price (string). */
    private String price;

    /** Message type (e.g. {@code "PLACEMENT"}, {@code "UPDATE"}, {@code "CANCELLATION"}). */
    @JsonProperty("type")
    private String msgType;

    /** Resolved outcome label (may be absent). */
    private String outcome;

    /** API key of the event owner (may be absent). */
    private String owner;

    /** API key of the order originator (may be absent). */
    @JsonProperty("order_owner")
    private String orderOwner;

    /** Original order size (may be absent). */
    @JsonProperty("original_size")
    private String originalSize;

    /** Amount matched so far (may be absent). */
    @JsonProperty("size_matched")
    private String sizeMatched;

    /** Unix timestamp of the event (may be absent). */
    private String timestamp;

    /** Associated trade IDs (may be absent). */
    @JsonProperty("associate_trades")
    private List<String> associateTrades;

    /** Order status (may be absent). */
    private String status;

    @Override
    public boolean isUser() { return true; }
}
