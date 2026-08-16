package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import java.util.Objects;

/** Shared construction guards for the trading intents. */
final class Intents {

    private Intents() {
    }

    static void requireTradeable(AssetId asset, Side side, Price price, ShareQuantity size) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(size, "size");
        if (size.isZero()) {
            throw new IllegalArgumentException("order size must be greater than zero");
        }
    }
}
