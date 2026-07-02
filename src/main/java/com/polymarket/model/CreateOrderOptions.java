package com.polymarket.model;

import lombok.Builder;

/**
 * Options for creating an order.
 *
 * @param tickSize Market tick size (e.g. "0.1", "0.01", "0.001", "0.0001")
 * @param negRisk  Whether this is a negative risk market
 */
@Builder
public record CreateOrderOptions(
    String tickSize,
    Boolean negRisk
) {}
