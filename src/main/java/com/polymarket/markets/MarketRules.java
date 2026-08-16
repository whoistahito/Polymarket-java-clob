package com.polymarket.markets;

import java.util.Objects;

/**
 * The live CLOB rules an order must satisfy. The minimum is in normalized SHARES, from
 * {@code /book.min_order_size} — Gamma's notional minimum must never be substituted here.
 */
public record MarketRules(TickSize tickSize, ShareQuantity minimumShares, boolean negativeRisk) {

    public MarketRules {
        Objects.requireNonNull(tickSize, "tickSize");
        Objects.requireNonNull(minimumShares, "minimumShares");
    }

    /** Rejects rather than rounds: a silently moved price is a different order. */
    public Price requireOnGrid(Price price) {
        if (!tickSize.isOnGrid(price)) {
            throw new IllegalArgumentException(
                    "price " + price + " is not on the " + tickSize + " tick grid");
        }
        return price;
    }

    public ShareQuantity requireAtLeastMinimum(ShareQuantity quantity) {
        if (quantity.value().compareTo(minimumShares.value()) < 0) {
            throw new IllegalArgumentException(
                    "size " + quantity + " is below the market minimum of " + minimumShares);
        }
        return quantity;
    }
}
