package com.polymarket.trading;

import com.polymarket.markets.AssetId;

/**
 * A fully signed V2 or V3 order, ready for the wire. {@code timestamp} is milliseconds for a
 * {@link com.polymarket.markets.TokenId} asset and seconds for a {@link com.polymarket.markets.PositionId}
 * one — the two exchanges document conflicting units and this type does not normalise them.
 * There is deliberately no nonce, fee-rate, taker, or version field: V1 cannot be expressed here.
 */
public record SignedOrder(
        long salt,
        String maker,
        String signer,
        AssetId asset,
        Side side,
        int signatureType,
        long makerAmount,
        long takerAmount,
        long timestamp,
        String metadata,
        String builder,
        String signature) {
}
