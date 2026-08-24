package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.Price;
import com.polymarket.markets.PriceLevel;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Derives immediate-order protection by walking enough live depth to cover the intent.
 * Pure: it reads a snapshot and performs no network work.
 */
public final class ImmediatePlanner {

    private static final int UNIT_SCALE = 6;

    private ImmediatePlanner() {
    }

    public static ImmediatePlan plan(@NonNull ImmediateBuy buy, @NonNull OrderBookSnapshot book) {
        requireSameAsset(buy.asset(), book);
        // The snapshot already sorted asks ascending, so "best first" is just iteration order.
        Optional<FeeRate> rate = buy.feeRate();
        BigDecimal remaining = buy.budget().value();
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        Price worst = null;

        for (PriceLevel level : eligible(book.asks(), buy.maximumPrice(), true)) {
            if (remaining.signum() <= 0) break;
            BigDecimal perShare = costPerShare(level.price(), rate);
            BigDecimal levelOutlay = perShare.multiply(level.size().value());
            BigDecimal taken;
            if (levelOutlay.compareTo(remaining) <= 0) {
                taken = level.size().value();
                remaining = remaining.subtract(levelOutlay);
            } else {
                // The quoted fee rounds to five decimals, so a partial fill keeps one unit of
                // headroom and value plus quoted fee still fits the budget.
                BigDecimal affordable = rate.isPresent()
                        ? remaining.subtract(FeeRate.SMALLEST_FEE) : remaining;
                taken = truncate(affordable.divide(perShare, UNIT_SCALE, RoundingMode.DOWN));
                if (taken.signum() <= 0) break;
                remaining = BigDecimal.ZERO;
            }
            shares = shares.add(taken);
            spent = spent.add(taken.multiply(level.price().value()));
            fees = fees.add(feeOn(rate, taken, level.price()));
            worst = level.price();
        }

        boolean partial = remaining.signum() > 0;
        ShareQuantity filled = ShareQuantity.of(truncate(shares));
        if (worst == null || (partial && buy.policy() == ExecutionPolicy.FOK)
                || belowMinimum(filled, book.rules())) {
            return new ImmediatePlan.InsufficientDepth(
                    PusdAmount.of(truncate(spent)), Optional.of(filled));
        }
        return new ImmediatePlan.Executable(worst, filled, PusdAmount.of(truncate(spent)),
                PusdAmount.of(fees.setScale(FeeRate.FEE_DECIMALS, RoundingMode.HALF_UP)), partial);
    }

    public static ImmediatePlan plan(@NonNull ImmediateSell sell, @NonNull OrderBookSnapshot book) {
        requireSameAsset(sell.asset(), book);
        book.rules().requireAtLeastMinimum(sell.size());
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
        ShareQuantity filled = ShareQuantity.of(truncate(shares));
        if (worst == null || (partial && sell.policy() == ExecutionPolicy.FOK)
                || belowMinimum(filled, book.rules())) {
            return new ImmediatePlan.InsufficientDepth(
                    PusdAmount.of(truncate(proceeds)), Optional.of(filled));
        }
        return new ImmediatePlan.Executable(worst, filled,
                PusdAmount.of(truncate(proceeds)), PusdAmount.of("0"), partial);
    }

    /** A book for another asset describes a different market's depth entirely. */
    private static void requireSameAsset(AssetId intended, OrderBookSnapshot book) {
        if (!intended.equals(book.asset())) {
            throw new IllegalArgumentException("order book for asset " + book.asset().value()
                    + " cannot plan an intent on asset " + intended.value());
        }
    }

    /** A fill under the live minimum is not a legal order, so it is depth the book cannot supply. */
    private static boolean belowMinimum(ShareQuantity filled, MarketRules rules) {
        return filled.value().compareTo(rules.minimumShares().value()) < 0;
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

    /**
     * Value plus fee for one share at this level, so a fee-aware budget buys what it can actually
     * afford. The fee is nonlinear in price, so it has to be charged level by level.
     */
    private static BigDecimal costPerShare(Price price, Optional<FeeRate> rate) {
        return rate.map(r -> price.value().add(
                        r.exactFeeOn(ShareQuantity.of(BigDecimal.ONE), price)))
                .orElse(price.value());
    }

    /** Exact per-level charge; the walk quotes the sum once, at the published five decimals. */
    private static BigDecimal feeOn(Optional<FeeRate> rate, BigDecimal shares, Price price) {
        return rate.map(r -> r.exactFeeOn(ShareQuantity.of(shares), price)).orElse(BigDecimal.ZERO);
    }

    private static BigDecimal truncate(BigDecimal value) {
        return value.setScale(UNIT_SCALE, RoundingMode.DOWN).stripTrailingZeros();
    }
}
