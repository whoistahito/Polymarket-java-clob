package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * New-market event ({@code event_type: "new_market"}).
 *
 * <p>Requires {@code custom_feature_enabled: true} on the subscription request.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewMarket extends WsMessage {

    /** Opaque market record ID. */
    private String id;

    /** Human-readable question. */
    private String question;

    /** Market condition ID. */
    private String market;

    /** URL slug. */
    private String slug;

    /** Market description. */
    private String description;

    /** List of asset / token IDs (field may be named {@code assets_ids} or {@code asset_ids}). */
    @JsonProperty("assets_ids")
    private List<String> assetIds;

    /** Outcome labels (e.g. {@code ["Yes","No"]}). */
    private List<String> outcomes;

    /** Optional enriched event message. */
    @JsonProperty("event_message")
    private EventMessage eventMessage;

    /** Unix timestamp in milliseconds (string on the wire). */
    private String timestamp;
}
