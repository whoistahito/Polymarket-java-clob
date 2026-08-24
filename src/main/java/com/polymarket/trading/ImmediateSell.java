package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import java.util.Objects;
import java.util.Optional;

/** An immediate SELL spends shares; the proceeds follow from the depth actually walked. */
public record ImmediateSell(
        AssetId asset, ShareQuantity size, ExecutionPolicy policy, Optional<Price> minimumPrice)
        implements OrderIntent {

    public ImmediateSell {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(minimumPrice, "minimumPrice");
        if (size.isZero()) {
            throw new IllegalArgumentException("an immediate SELL needs a size above zero");
        }
    }

    public static ImmediateSell of(AssetId asset, ShareQuantity size, ExecutionPolicy policy) {
        return new ImmediateSell(asset, size, policy, Optional.empty());
    }

    /** A stricter caller boundary; the derived protected price may not fall below it. */
    public ImmediateSell notBelow(Price minimum) {
        return new ImmediateSell(asset, size, policy,
                Optional.of(Objects.requireNonNull(minimum, "minimum")));
    }

    @Override
    public Side side() {
        return Side.SELL;
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
