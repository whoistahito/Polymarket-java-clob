package com.polymarket.trading;

import com.polymarket.markets.AssetId;

/**
 * What the caller wants to trade, in the units they actually hold. The subtype fixes both
 * the execution promise and the unit, so one ambiguous amount cannot mean two things.
 */
public sealed interface OrderIntent
        permits LimitOrder, MakerOnlyLimitOrder, GoodTilDateOrder, ImmediateBuy, ImmediateSell {

    AssetId asset();

    Side side();

    /** The wire order type this intent asks for; submission derives it rather than restating it. */
    OrderType orderType();

    /** True only for an intent that refuses to take liquidity. */
    boolean postOnly();

    /** Wire expiration in unix seconds; 0 for every type but GTD. */
    long expirationSeconds();
}
