package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;

/** A resting limit order. It is price-controlled but may cross, so it promises nothing about maker status. */
public record LimitOrder(AssetId asset, Side side, Price price, ShareQuantity size)
        implements OrderIntent {

    public LimitOrder {
        Intents.requireTradeable(asset, side, price, size);
    }

    public boolean postOnly() {
        return false;
    }
}
