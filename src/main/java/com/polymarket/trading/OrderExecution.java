package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import lombok.NonNull;

/**
 * An Order Intent resolved against its Market Rule Snapshot: the exact legs, order type,
 * post-only promise and lifetime that reach the wire. Valid by construction, so nothing an
 * intent promised can be dropped between planning and submission.
 */
public record OrderExecution(
        @NonNull AssetId asset,
        @NonNull Side side,
        @NonNull Price price,
        @NonNull ShareQuantity shares,
        @NonNull PusdAmount pusdLeg,
        @NonNull MarketRules rules,
        @NonNull OrderType orderType,
        boolean postOnly,
        long expirationSeconds) {

    public OrderExecution {
        rules.requireExecutable(price, shares);
    }

    public static OrderExecution of(@NonNull LimitOrder intent, @NonNull MarketRules rules) {
        return resting(intent.asset(), intent.side(), intent.price(), intent.size(), rules, false);
    }

    public static OrderExecution of(@NonNull MakerOnlyLimitOrder intent, @NonNull MarketRules rules) {
        return resting(intent.asset(), intent.side(), intent.price(), intent.size(), rules, true);
    }

    /** The expiration validated at intent construction travels on, shifted by the official buffer. */
    public static OrderExecution of(@NonNull GoodTilDateOrder intent, @NonNull MarketRules rules) {
        return new OrderExecution(intent.asset(), intent.side(), intent.price(), intent.size(),
                rules.notional(intent.price(), intent.size()), rules, OrderType.GTD, false,
                intent.expirationSeconds());
    }

    /** Legs priced at the plan's Protected Price, never at the blended average of the walk. */
    public static OrderExecution of(@NonNull ImmediateBuy intent,
            @NonNull ImmediatePlan.Executable plan, @NonNull MarketRules rules) {
        return immediate(intent.asset(), Side.BUY, plan, intent.policy(), rules);
    }

    /** Legs priced at the plan's Protected Price, never at the blended average of the walk. */
    public static OrderExecution of(@NonNull ImmediateSell intent,
            @NonNull ImmediatePlan.Executable plan, @NonNull MarketRules rules) {
        return immediate(intent.asset(), Side.SELL, plan, intent.policy(), rules);
    }

    public SignedOrder sign(@NonNull OrderSigner signer, @NonNull SigningContext context) {
        return signer.sign(asset, side, price, shares, rules, context);
    }

    public OrderPlacement placement(@NonNull ApiCredentials credentials) {
        return new OrderPlacement(credentials, orderType, expirationSeconds, postOnly);
    }

    private static OrderExecution resting(AssetId asset, Side side, Price price,
            ShareQuantity size, MarketRules rules, boolean postOnly) {
        return new OrderExecution(asset, side, price, size, rules.notional(price, size), rules,
                OrderType.GTC, postOnly, 0L);
    }

    private static OrderExecution immediate(AssetId asset, Side side,
            ImmediatePlan.Executable plan, ExecutionPolicy policy, MarketRules rules) {
        Price protectedPrice = plan.protectedPrice();
        return new OrderExecution(asset, side, protectedPrice, plan.shares(),
                rules.notional(protectedPrice, plan.shares()), rules,
                policy.orderType(), false, 0L);
    }
}
