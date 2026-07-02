package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/**
 * Filter parameters for {@code GET /orders} (open orders list).
 *
 * <p>Mirrors the TypeScript {@code OpenOrderParams} interface.
 * All fields are optional; {@code null} means "no filter".
 */
@Value
@Builder
public class OpenOrderParams {

    /** Filter by a specific order ID. */
    String id;

    /** Filter by market (condition ID). */
    String market;

    /** Filter by asset / token ID. */
    @lombok.Builder.Default
    String assetId = null;
}
