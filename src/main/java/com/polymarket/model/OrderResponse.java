package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

/**
 * Response received after posting an order.
 *
 * @param success Whether the operation succeeded
 * @param errorMsg Error message if failed
 * @param orderID The unique ID of the created order
 * @param transactionsHashes Related transaction hashes
 * @param tradeIDs Related trade IDs
 * @param status Order status
 * @param takingAmount Amount taken
 * @param makingAmount Amount made
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponse(
    boolean success,
    String errorMsg,
    @JsonProperty("orderID") String orderID,
    List<String> transactionsHashes,
    List<String> tradeIDs,
    String status,
    String takingAmount,
    String makingAmount) {}
