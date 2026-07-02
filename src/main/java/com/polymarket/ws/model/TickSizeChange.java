package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Tick-size change notification ({@code event_type: "tick_size_change"}).
 *
 * <p>Emitted when the backend adjusts the minimum price increment for an asset.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TickSizeChange extends WsMessage {

    /** Asset / token identifier. */
    @JsonProperty("asset_id")
    private String assetId;

    /** Market condition ID. */
    private String market;

    /** Previous tick size (string). */
    @JsonProperty("old_tick_size")
    private String oldTickSize;

    /** New tick size (string). */
    @JsonProperty("new_tick_size")
    private String newTickSize;

    /** Unix timestamp in milliseconds (string on the wire). */
    private String timestamp;
}
