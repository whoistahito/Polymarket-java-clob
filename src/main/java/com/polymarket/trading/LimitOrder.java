package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import lombok.NonNull;

/** A resting limit order. It is price-controlled but may cross, so it promises nothing about maker status. */
public record LimitOrder(@NonNull AssetId asset, @NonNull Side side, @NonNull Price price,
        @NonNull ShareQuantity size)
        implements OrderIntent {

    public LimitOrder {
        Intents.requireTradeable(asset, side, price, size);
    }

    @Override
    public OrderType orderType() {
        return OrderType.GTC;
    }

    @Override
    public boolean postOnly() {
        return false;
    }

    @Override
    public long expirationSeconds() {
        return 0L;
    }
}
