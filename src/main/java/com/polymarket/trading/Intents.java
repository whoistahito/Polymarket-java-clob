package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import lombok.NonNull;

/** Shared construction guards for the trading intents. */
final class Intents {

    private Intents() {
    }

    static void requireTradeable(@NonNull AssetId asset, @NonNull Side side, @NonNull Price price,
            @NonNull ShareQuantity size) {
        if (size.isZero()) {
            throw new IllegalArgumentException("order size must be greater than zero");
        }
    }
}
