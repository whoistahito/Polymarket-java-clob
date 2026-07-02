package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/** A single historical price data point (timestamp + price). */
@Value
public class MarketPrice {

    /** Unix timestamp in seconds. */
    @JsonProperty("t")
    long t;

    /** Price value. */
    @JsonProperty("p")
    double p;
}
