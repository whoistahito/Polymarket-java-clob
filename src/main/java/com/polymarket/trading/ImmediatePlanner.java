package com.polymarket.trading;

import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.Price;
import com.polymarket.markets.PriceLevel;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Derives immediate-order protection by walking enough live depth to cover the intent.
 * Pure: it reads a snapshot and performs no network work.
 */
public final class ImmediatePlanner {

    private static final int UNIT_SCALE = 6;

    private ImmediatePlanner() {
    }

    public static ImmediatePlan plan(ImmediateBuy buy, OrderBookSnapshot book) {
        // The snapshot already sorted asks ascending, so "best first" is just iteration order.
        BigDecimal spendable = spendableAfterFee(buy);
        BigDecimal remaining = spendable;
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        Price worst = null;

        for (PriceLevel level : eligible(book.asks(), buy.maximumPrice(), true)) {
            if (remaining.signum() <= 0) break;
            BigDecimal levelCost = level.price().value().multiply(level.size().value());
            if (levelCost.compareTo(remaining) <= 0) {
                shares = shares.add(level.size().value());
                spent = spent.add(levelCost);
                remaining = remaining.subtract(levelCost);
            } else {
                BigDecimal partialShares = truncate(remaining.divide(
                        level.price().value(), UNIT_SCALE, RoundingMode.DOWN));
                if (partialShares.signum() <= 0) break;
                shares = shares.add(partialShares);
                spent = spent.add(partialShares.multiply(level.price().value()));
                remaining = BigDecimal.ZERO;
            }
            worst = level.price();
        }

        boolean partial = remaining.signum() > 0;
        if (worst == null || (partial && buy.policy() == ExecutionPolicy.FOK)) {
            return new ImmediatePlan.InsufficientDepth(
                    PusdAmount.of(truncate(spent)), Optional.of(ShareQuantity.of(truncate(shares))));
        }
        PusdAmount cost = PusdAmount.of(truncate(spent));
        return new ImmediatePlan.Executable(worst, ShareQuantity.of(truncate(shares)), cost,
                feeOn(buy, cost), partial);
    }

    public static ImmediatePlan plan(ImmediateSell sell, OrderBookSnapshot book) {
        BigDecimal remaining = sell.size().value();
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal proceeds = BigDecimal.ZERO;
        Price worst = null;

        for (PriceLevel level : eligible(book.bids(), sell.minimumPrice(), false)) {
            if (remaining.signum() <= 0) break;
            BigDecimal take = level.size().value().min(remaining);
            shares = shares.add(take);
            proceeds = proceeds.add(take.multiply(level.price().value()));
            remaining = remaining.subtract(take);
            worst = level.price();
        }

        boolean partial = remaining.signum() > 0;
        if (worst == null || (partial && sell.policy() == ExecutionPolicy.FOK)) {
            return new ImmediatePlan.InsufficientDepth(
                    PusdAmount.of(truncate(proceeds)), Optional.of(ShareQuantity.of(truncate(shares))));
        }
        return new ImmediatePlan.Executable(worst, ShareQuantity.of(truncate(shares)),
                PusdAmount.of(truncate(proceeds)), PusdAmount.of("0"), partial);
    }

    /** Levels the caller's boundary permits; the snapshot already ordered them best-first. */
    private static List<PriceLevel> eligible(
            List<PriceLevel> levels, Optional<Price> boundary, boolean buying) {
        return boundary.map(limit -> levels.stream()
                        .filter(level -> buying
                                ? level.price().compareTo(limit) <= 0
                                : level.price().compareTo(limit) >= 0)
                        .toList())
                .orElse(levels);
    }

    /** Reserves the worst-case fee up front so value + fee cannot exceed the budget. */
    private static BigDecimal spendableAfterFee(ImmediateBuy buy) {
        return buy.feeRate()
                .map(rate -> buy.budget().value().multiply(BigDecimal.valueOf(10_000))
                        .divide(BigDecimal.valueOf(10_000L + rate.basisPoints()),
                                UNIT_SCALE, RoundingMode.DOWN))
                .orElse(buy.budget().value());
    }

    private static PusdAmount feeOn(ImmediateBuy buy, PusdAmount cost) {
        return buy.feeRate()
                .map(rate -> PusdAmount.of(truncate(rate.feeOn(cost.value()))))
                .orElse(PusdAmount.of("0"));
    }

    private static BigDecimal truncate(BigDecimal value) {
        return value.setScale(UNIT_SCALE, RoundingMode.DOWN).stripTrailingZeros();
    }
}
