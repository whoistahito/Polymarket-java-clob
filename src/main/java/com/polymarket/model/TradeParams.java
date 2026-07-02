package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/**
 * Filter parameters for {@code GET /data/trades} (trade history).
 *
 * <p>Mirrors the TypeScript {@code TradeParams} interface.
 * All fields are optional; {@code null} means "no filter".
 */
@Value
@Builder
public class TradeParams {

    /** Filter by trade ID. */
    String id;

    /** Filter by maker wallet address. */
    String makerAddress;

    /** Filter by market (condition ID). */
    String market;

    /** Filter by asset / token ID. */
    String assetId;

    /** Upper bound on trade timestamp (ISO-8601 or epoch string). */
    String before;

    /** Lower bound on trade timestamp (ISO-8601 or epoch string). */
    String after;
}
