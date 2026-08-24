package com.polymarket.markets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.NonNull;

/**
 * The live CLOB rules an order must satisfy. The minimum is in normalized SHARES, from
 * {@code /book.min_order_size} — Gamma's notional minimum must never be substituted here.
 */
public record MarketRules(
        @NonNull TickSize tickSize, @NonNull ShareQuantity minimumShares, boolean negativeRisk) {

    private static final BigDecimal ONE = BigDecimal.ONE;

    /** Rejects rather than rounds: a silently moved price is a different order. */
    public Price requireOnGrid(@NonNull Price price) {
        if (!tickSize.isOnGrid(price)) {
            throw new IllegalArgumentException(
                    "price " + price + " is not on the " + tickSize + " tick grid");
        }
        return price;
    }

    public ShareQuantity requireAtLeastMinimum(@NonNull ShareQuantity quantity) {
        if (quantity.value().compareTo(minimumShares.value()) < 0) {
            throw new IllegalArgumentException(
                    "size " + quantity + " is below the market minimum of " + minimumShares);
        }
        return quantity;
    }

    /**
     * Universal bound: 0 and 1 are settled outcomes, not order prices. Market-specific bound:
     * the grid runs from one tick to one tick below certainty.
     */
    public Price requireWithinBounds(@NonNull Price price) {
        BigDecimal highest = ONE.subtract(tickSize.value());
        if (price.value().signum() <= 0 || price.value().compareTo(ONE) >= 0) {
            throw new IllegalArgumentException(
                    "price " + price + " is outside the tradeable range (0, 1)");
        }
        if (price.value().compareTo(tickSize.value()) < 0
                || price.value().compareTo(highest) > 0) {
            throw new IllegalArgumentException("price " + price + " is outside the "
                    + tickSize + " market bounds [" + tickSize.value().toPlainString()
                    + ", " + highest.toPlainString() + "]");
        }
        return price;
    }

    /** Bounds, grid and live minimum together — everything that must hold before encoding. */
    public void requireExecutable(@NonNull Price price, @NonNull ShareQuantity shares) {
        requireWithinBounds(price);
        requireOnGrid(price);
        requireAtLeastMinimum(shares);
    }

    /**
     * The pUSD leg for a price and size, at this grid's documented amount precision: round up to
     * amount decimals + 4, then down to amount decimals.
     */
    public PusdAmount notional(@NonNull Price price, @NonNull ShareQuantity shares) {
        int decimals = tickSize.amountDecimals();
        return PusdAmount.of(price.value().multiply(shares.value())
                .setScale(decimals + 4, RoundingMode.UP)
                .setScale(decimals, RoundingMode.DOWN));
    }
}
