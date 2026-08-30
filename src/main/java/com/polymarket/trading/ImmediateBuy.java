package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import java.util.Optional;
import lombok.NonNull;

/** An immediate BUY spends pUSD; the share count follows from the depth actually walked. */
public record ImmediateBuy(
        @NonNull AssetId asset,
        @NonNull PusdAmount budget,
        @NonNull ExecutionPolicy policy,
        @NonNull Optional<Price> maximumPrice,
        @NonNull Optional<FeeRate> feeRate)
        implements OrderIntent {

    public ImmediateBuy {
        if (budget.isZero()) {
            throw new IllegalArgumentException("an immediate BUY needs a budget above zero");
        }
    }

    public static ImmediateBuy of(AssetId asset, PusdAmount budget, ExecutionPolicy policy) {
        return new ImmediateBuy(asset, budget, policy, Optional.empty(), Optional.empty());
    }

    /** A stricter caller boundary; the derived protected price may not exceed it. */
    public ImmediateBuy notAbove(@NonNull Price maximum) {
        return new ImmediateBuy(asset, budget, policy, Optional.of(maximum), feeRate);
    }

    /** Makes the budget fee-aware, so order value plus fees stays inside it. */
    public ImmediateBuy withFeeRate(@NonNull FeeRate rate) {
        return new ImmediateBuy(asset, budget, policy, maximumPrice, Optional.of(rate));
    }

    @Override
    public Side side() {
        return Side.BUY;
    }

    @Override
    public OrderType orderType() {
        return policy.orderType();
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
