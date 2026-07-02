package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Enriched event metadata attached to {@link NewMarket} and {@link MarketResolved}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    private String id;
    private String ticker;
    private String slug;
    private String title;
    private String description;
}
