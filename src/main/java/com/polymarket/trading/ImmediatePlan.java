package com.polymarket.trading;

import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.util.Optional;
import lombok.NonNull;

/**
 * What walking live depth produced. Insufficient depth is a business outcome, not an
 * exception, so a caller can decide between waiting, resizing, or giving up.
 */
public sealed interface ImmediatePlan {

    /**
     * A fillable plan. {@code protectedPrice} is the least crossing price that still fills, and
     * {@code cost} is the pUSD leg the order will carry at it — the most a BUY can spend and the
     * least a SELL can receive — not the blended value of the walk.
     */
    record Executable(
            @NonNull Price protectedPrice,
            @NonNull ShareQuantity shares,
            @NonNull PusdAmount cost,
            @NonNull PusdAmount fee,
            boolean partial)
            implements ImmediatePlan {

    }

    /** FOK could not be filled, or the book had nothing eligible. */
    record InsufficientDepth(@NonNull PusdAmount available,
            @NonNull Optional<ShareQuantity> availableShares) implements ImmediatePlan {
    }
}
