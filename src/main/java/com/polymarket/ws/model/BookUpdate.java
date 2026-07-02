package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Full or incremental order-book snapshot ({@code event_type: "book"}).
 *
 * <p>Received when first subscribing to a market asset or when a trade occurs.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookUpdate extends WsMessage {

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** Market condition ID. */
    private String market;

    /** Unix timestamp in milliseconds (received as a string from the wire). */
    private String timestamp;

    /** Current bid levels (highest price first). */
    private List<OrderBookLevel> bids;

    /** Current ask levels (lowest price first). */
    private List<OrderBookLevel> asks;

    /** Hash for order-book validation. */
    private String hash;
}
