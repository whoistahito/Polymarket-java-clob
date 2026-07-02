package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Payload sent to the API to post an order.
 *
 * @param order The signed order structure
 * @param owner The API key of the order owner
 * @param orderType The type of order (GTC, FOK, etc.)
 * @param deferExec Whether to defer execution
 * @param postOnly Whether the order is post-only (only applicable to GTC/GTD)
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostOrderPayload(
    SignedOrder order, String owner, OrderType orderType, boolean deferExec, Boolean postOnly) {}
