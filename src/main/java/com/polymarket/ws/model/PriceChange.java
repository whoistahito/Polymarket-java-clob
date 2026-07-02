package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Price-change notification ({@code event_type: "price_change"}).
 *
 * <p>Can contain a batch of individual {@link PriceChangeBatchEntry} items.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceChange extends WsMessage {

    /** Market condition ID. */
    private String market;

    /** Unix timestamp in milliseconds (string on the wire). */
    private String timestamp;

    /** Individual price-change entries in this batch. */
    @JsonProperty("price_changes")
    private List<PriceChangeBatchEntry> priceChanges;
}
