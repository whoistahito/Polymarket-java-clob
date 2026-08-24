package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;

/** A limit order the exchange must reject if it would take liquidity; maps to postOnly. */
public record MakerOnlyLimitOrder(AssetId asset, Side side, Price price, ShareQuantity size)
        implements OrderIntent {

    public MakerOnlyLimitOrder {
        Intents.requireTradeable(asset, side, price, size);
    }

    @Override
    public OrderType orderType() {
        return OrderType.GTC;
    }

    @Override
    public boolean postOnly() {
        return true;
    }

    @Override
    public long expirationSeconds() {
        return 0L;
    }
}
