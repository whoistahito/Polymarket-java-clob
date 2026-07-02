package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single RFQ request record. Mirrors TS {@code RfqRequest}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqRequest {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("userAddress")
    private String userAddress;

    @JsonProperty("proxyAddress")
    private String proxyAddress;

    @JsonProperty("token")
    private String token;

    @JsonProperty("complement")
    private String complement;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("side")
    private String side;

    @JsonProperty("sizeIn")
    private String sizeIn;

    @JsonProperty("sizeOut")
    private String sizeOut;

    @JsonProperty("price")
    private double price;

    @JsonProperty("acceptedQuoteId")
    private String acceptedQuoteId;

    @JsonProperty("state")
    private String state;

    @JsonProperty("expiry")
    private String expiry;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;
}
