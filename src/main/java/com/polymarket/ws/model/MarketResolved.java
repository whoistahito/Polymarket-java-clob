package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Market-resolved event ({@code event_type: "market_resolved"}).
 *
 * <p>Requires {@code custom_feature_enabled: true} on the subscription request.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketResolved extends WsMessage {

    /** Opaque market record ID. */
    private String id;

    /** Human-readable question (may be absent). */
    private String question;

    /** Market condition ID. */
    private String market;

    /** URL slug (may be absent). */
    private String slug;

    /** Market description (may be absent). */
    private String description;

    /** List of asset / token IDs. */
    @JsonProperty("assets_ids")
    private List<String> assetIds;

    /** Outcome labels. */
    private List<String> outcomes;

    /** Asset ID of the winning outcome. */
    @JsonProperty("winning_asset_id")
    private String winningAssetId;

    /** Label of the winning outcome (e.g. {@code "Yes"}). */
    @JsonProperty("winning_outcome")
    private String winningOutcome;

    /** Optional enriched event message. */
    @JsonProperty("event_message")
    private EventMessage eventMessage;

    /** Unix timestamp in milliseconds (string on the wire). */
    private String timestamp;
}
