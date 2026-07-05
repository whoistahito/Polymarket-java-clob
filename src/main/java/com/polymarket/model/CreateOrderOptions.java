package com.polymarket.model;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Options for creating an order.
 *
 * @param tickSize     Market tick size (e.g. "0.1", "0.01", "0.001", "0.0001")
 * @param negRisk      Whether this is a negative risk market
 * @param orderMinSize Per-market minimum order size in shares (the market's {@code orderMinSize} /
 *                     order book {@code min_order_size}). When set, order construction rejects a
 *                     smaller size client-side. Optional — leave null to let the exchange enforce it.
 */
@Builder
public record CreateOrderOptions(
    String tickSize,
    Boolean negRisk,
    BigDecimal orderMinSize
) {}
