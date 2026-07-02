package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single RFQ quote record. Mirrors TS {@code RfqQuote}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqQuote {

    @JsonProperty("quoteId")
    private String quoteId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("userAddress")
    private String userAddress;

    @JsonProperty("proxyAddress")
    private String proxyAddress;

    @JsonProperty("complement")
    private String complement;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("token")
    private String token;

    @JsonProperty("side")
    private String side;

    @JsonProperty("sizeIn")
    private String sizeIn;

    @JsonProperty("sizeOut")
    private String sizeOut;

    @JsonProperty("price")
    private double price;

    @JsonProperty("state")
    private String state;

    @JsonProperty("expiry")
    private String expiry;

    @JsonProperty("matchType")
    private String matchType;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;
}
