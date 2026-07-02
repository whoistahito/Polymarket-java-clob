package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * User trade-execution message ({@code event_type: "trade"}).
 *
 * <p>Only received on the authenticated user channel.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeMessage extends WsMessage {

    /** Trade identifier. */
    private String id;

    /** Market condition ID. */
    private String market;

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** Side ({@code "BUY"} or {@code "SELL"}). */
    private String side;

    /** Trade size (string to preserve precision). */
    private String size;

    /** Execution price (string). */
    private String price;

    /** Trade status (e.g. {@code "MATCHED"}, {@code "MINED"}, {@code "CONFIRMED"}). */
    private String status;

    /** Message type (e.g. {@code "trade"}). */
    @JsonProperty("type")
    private String msgType;

    /** Timestamp of last trade modification (string on the wire; may be absent). */
    @JsonProperty("last_update")
    private String lastUpdate;

    /** Time trade was matched (may be absent). Live wire key is {@code matchtime}; {@code match_time} accepted as alias. */
    @JsonProperty("matchtime")
    @JsonAlias("match_time")
    private String matchTime;

    /** Unix timestamp of the event (may be absent). */
    private String timestamp;

    /** Resolved outcome label (may be absent). */
    private String outcome;

    /** API key of the event owner (may be absent). */
    private String owner;

    /** API key of the trade owner (may be absent). */
    @JsonProperty("trade_owner")
    private String tradeOwner;

    /** ID of the taker order (may be absent). */
    @JsonProperty("taker_order_id")
    private String takerOrderId;

    /** Maker order details (may be empty). */
    @JsonProperty("maker_orders")
    private List<WsMakerOrder> makerOrders;

    /** Fee rate in basis points (may be absent). */
    @JsonProperty("fee_rate_bps")
    private String feeRateBps;

    /** On-chain transaction hash (may be absent). */
    @JsonProperty("transaction_hash")
    private String transactionHash;

    /** Whether the user was maker or taker (may be absent). */
    @JsonProperty("trader_side")
    private String traderSide;

    @Override
    public boolean isUser() { return true; }
}
