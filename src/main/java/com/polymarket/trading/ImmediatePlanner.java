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
        MarketRules rules = book.rules();
        Optional<FeeRate> rate = buy.feeRate();
        // The signed order reprices every share at the protected price, so affordability has to be
        // measured there too: a blended walk buys shares the repriced leg cannot pay for.
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal shares = BigDecimal.ZERO;
        Price protectedPrice = null;
        boolean budgetBound = false;

        // The snapshot already sorted asks ascending, so "best first" is just iteration order.
        List<PriceLevel> asks = eligible(book.asks(), buy.maximumPrice(), true);
        for (PriceLevel level : asks) {
            available = available.add(level.size().value());
            BigDecimal affordable = affordableAt(level.price(), buy.budget().value(), rate, rules);
            // Only depth that survives the size grid can be bought, so a sub-grid tail at a worse
            // price must not lift the protected price for shares the order cannot carry.
            BigDecimal onGridDepth = truncateSize(available, rules);
            BigDecimal candidate = onGridDepth.min(affordable);
            if (candidate.compareTo(shares) > 0) {
                shares = candidate;
                protectedPrice = level.price();
                // A complete fill is one the budget — not the book — sized. Read it where the
                // quantity is actually set: deeper, pricier depth the budget can never protect at
                // must not fake a full spend the chosen order never made.
                budgetBound = affordable.compareTo(onGridDepth) <= 0;
            }
            // Deeper levels cost more per share, so once the budget binds it can only buy less.
            if (affordable.compareTo(available) <= 0) {
                break;
            }
        }

        boolean partial = !budgetBound;
        ShareQuantity filled = ShareQuantity.of(shares);
        if (protectedPrice == null || (partial && buy.policy() == ExecutionPolicy.FOK)
                || belowMinimum(filled, rules)) {
            // An unfillable report describes the book, so it quotes the walk at its own level
            // prices rather than the single price an order would have been signed at.
            return new ImmediatePlan.InsufficientDepth(
                    blendedValue(asks, shares, rules), Optional.of(filled));
        }
        return new ImmediatePlan.Executable(protectedPrice, filled,
                rules.notional(protectedPrice, filled), quotedFee(rate, filled, protectedPrice),
                partial);
    }

    /**
     * The most shares this budget can carry when every one of them is repriced at {@code price}:
     * value plus fee at that price, then trimmed until the encoded pUSD leg itself fits.
     */
    private static BigDecimal affordableAt(Price price, BigDecimal budget, Optional<FeeRate> rate,
            MarketRules rules) {
        BigDecimal candidate = truncateSize(
                budget.divide(costPerShare(price, rate), UNIT_SCALE, RoundingMode.DOWN), rules);
        BigDecimal step = BigDecimal.ONE.movePointLeft(rules.tickSize().sizeDecimals());
        // Encoding rounds the leg and the fee, so the fit is verified rather than assumed.
        while (candidate.signum() > 0
                && authorised(price, candidate, rate, rules).compareTo(budget) > 0) {
            candidate = candidate.subtract(step);
        }
        return candidate.max(BigDecimal.ZERO);
    }

    private static BigDecimal authorised(Price price, BigDecimal shares, Optional<FeeRate> rate,
            MarketRules rules) {
        ShareQuantity quantity = ShareQuantity.of(shares);
        return rules.notional(price, quantity).value()
                .add(quotedFee(rate, quantity, price).value());
    }

    private static PusdAmount quotedFee(Optional<FeeRate> rate, ShareQuantity shares, Price price) {
        return rate.map(r -> r.feeOn(shares, price)).orElse(PusdAmount.of("0"));
    }

    /** What the first {@code shares} of this depth are worth at their own level prices. */
    private static PusdAmount blendedValue(List<PriceLevel> levels, BigDecimal shares,
            MarketRules rules) {
        BigDecimal remaining = shares;
        BigDecimal value = BigDecimal.ZERO;
        for (PriceLevel level : levels) {
            if (remaining.signum() <= 0) break;
            BigDecimal taken = level.size().value().min(remaining);
            value = value.add(level.price().value().multiply(taken));
            remaining = remaining.subtract(taken);
        }
        return PusdAmount.of(truncateAmount(value, rules));
    }

    public static ImmediatePlan plan(@NonNull ImmediateSell sell, @NonNull OrderBookSnapshot book) {
        requireSameAsset(sell.asset(), book);
        MarketRules rules = book.rules();
        rules.requireAtLeastMinimum(sell.size());
        List<PriceLevel> bids = eligible(book.bids(), sell.minimumPrice(), false);
        BigDecimal remaining = sell.size().value();
        BigDecimal walked = BigDecimal.ZERO;
        BigDecimal shares = BigDecimal.ZERO;
        Price worst = null;

        for (PriceLevel level : bids) {
            if (remaining.signum() <= 0) break;
            BigDecimal take = level.size().value().min(remaining);
            walked = walked.add(take);
            remaining = remaining.subtract(take);
            // The floor only follows the shares the order keeps: depth the size grid discards
            // must not drag the protected price down with it.
            BigDecimal retained = truncateSize(walked, rules);
            if (retained.compareTo(shares) > 0) {
                shares = retained;
                worst = level.price();
            }
        }

        // What the order can actually sell is what stayed on the grid, so a size the grid cannot
        // express is a partial fill even when the book covered every share of it.
        boolean partial = shares.compareTo(sell.size().value()) < 0;
        ShareQuantity filled = ShareQuantity.of(shares);
        if (worst == null || (partial && sell.policy() == ExecutionPolicy.FOK)
                || belowMinimum(filled, rules)) {
            return new ImmediatePlan.InsufficientDepth(
                    blendedValue(bids, shares, rules), Optional.of(filled));
        }
        return new ImmediatePlan.Executable(worst, filled, rules.notional(worst, filled),
                PusdAmount.of("0"), partial);
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

    /** The documented size precision for this grid, never a fixed six decimals. */
    private static BigDecimal truncateSize(BigDecimal value, MarketRules rules) {
        return value.setScale(rules.tickSize().sizeDecimals(), RoundingMode.DOWN)
                .stripTrailingZeros();
    }

    private static BigDecimal truncateAmount(BigDecimal value, MarketRules rules) {
        return value.setScale(rules.tickSize().amountDecimals(), RoundingMode.DOWN)
                .stripTrailingZeros();
    }
}
