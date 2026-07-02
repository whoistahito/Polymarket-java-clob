package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Live activity event for a market. Mirrors TS {@code MarketTradeEvent}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketTradeEvent {

    @JsonProperty("event_type")
    private String eventType;

    /** Nested market object from the event payload. */
    @JsonProperty("market")
    private Map<String, Object> market;

    /** Nested user object from the event payload. */
    @JsonProperty("user")
    private Map<String, Object> user;

    @JsonProperty("side")
    private String side;

    @JsonProperty("size")
    private String size;

    @JsonProperty("fee_rate_bps")
    private String feeRateBps;

    @JsonProperty("price")
    private String price;

    @JsonProperty("outcome")
    private String outcome;

    @JsonProperty("outcome_index")
    private Integer outcomeIndex;

    @JsonProperty("transaction_hash")
    private String transactionHash;

    @JsonProperty("timestamp")
    private String timestamp;
}
