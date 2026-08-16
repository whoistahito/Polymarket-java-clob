package com.polymarket.markets;

/**
 * What an order trades. The subtype alone selects the exchange and typed data, so routing
 * never depends on inspecting the digits.
 */
public sealed interface AssetId permits TokenId, PositionId {

    String value();
}
