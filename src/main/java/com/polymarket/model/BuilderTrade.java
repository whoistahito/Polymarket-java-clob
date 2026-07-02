package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A trade record from the builder trades endpoint. Mirrors TS {@code BuilderTrade}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuilderTrade {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tradeType")
    private String tradeType;

    @JsonProperty("takerOrderHash")
    private String takerOrderHash;

    @JsonProperty("builder")
    private String builder;

    @JsonProperty("market")
    private String market;

    @JsonProperty("assetId")
    private String assetId;

    @JsonProperty("side")
    private String side;

    @JsonProperty("size")
    private String size;

    @JsonProperty("sizeUsdc")
    private String sizeUsdc;

    @JsonProperty("price")
    private String price;

    @JsonProperty("status")
    private String status;

    @JsonProperty("outcome")
    private String outcome;

    @JsonProperty("outcomeIndex")
    private int outcomeIndex;

    @JsonProperty("owner")
    private String owner;

    @JsonProperty("maker")
    private String maker;

    @JsonProperty("transactionHash")
    private String transactionHash;

    @JsonProperty("matchTime")
    private String matchTime;

    @JsonProperty("bucketIndex")
    private int bucketIndex;

    @JsonProperty("fee")
    private String fee;

    @JsonProperty("feeUsdc")
    private String feeUsdc;

    @JsonProperty("err_msg")
    private String errMsg;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;
}
