package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import lombok.NonNull;

/**
 * Signs a resolved order leg pair entirely offline. The asset's sealed type is the only
 * routing decision: a {@link com.polymarket.markets.TokenId} signs against Exchange V2, a
 * {@link com.polymarket.markets.PositionId} against Exchange V3 — no string heuristics.
 */
public interface OrderSigner {

    /**
     * {@code pusdLeg}/{@code shareLeg} are the two order legs; {@code side} decides which is
     * maker and which is taker. {@code rules.negativeRisk()} selects the V2 neg-risk contract.
     */
    SignedOrder sign(AssetId asset, Side side, PusdAmount pusdLeg, ShareQuantity shareLeg,
            MarketRules rules, SigningContext context);

    /**
     * The priced seam: the Market Rule Snapshot is enforced — tradeable bounds, tick grid and
     * live minimum shares — and the pUSD leg encoded at the grid's precision, before any signing.
     */
    default SignedOrder sign(@NonNull AssetId asset, @NonNull Side side, @NonNull Price price,
            @NonNull ShareQuantity shares, @NonNull MarketRules rules,
            @NonNull SigningContext context) {
        rules.requireExecutable(price, shares);
        return sign(asset, side, rules.notional(price, shares), shares, rules, context);
    }
}
