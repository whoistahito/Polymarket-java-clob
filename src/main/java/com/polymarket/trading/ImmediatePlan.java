package com.polymarket.trading;

import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.util.Objects;
import java.util.Optional;

/**
 * What walking live depth produced. Insufficient depth is a business outcome, not an
 * exception, so a caller can decide between waiting, resizing, or giving up.
 */
public sealed interface ImmediatePlan {

    /**
     * A fillable plan. {@code cost} is spend for a BUY and proceeds for a SELL;
     * {@code protectedPrice} is the least crossing price that still fills.
     */
    record Executable(
            Price protectedPrice,
            ShareQuantity shares,
            PusdAmount cost,
            PusdAmount fee,
            boolean partial)
            implements ImmediatePlan {

        public Executable {
            Objects.requireNonNull(protectedPrice, "protectedPrice");
            Objects.requireNonNull(shares, "shares");
            Objects.requireNonNull(cost, "cost");
            Objects.requireNonNull(fee, "fee");
        }
    }

    /** FOK could not be filled, or the book had nothing eligible. */
    record InsufficientDepth(PusdAmount available, Optional<ShareQuantity> availableShares)
            implements ImmediatePlan {

        public InsufficientDepth {
            Objects.requireNonNull(available, "available");
            Objects.requireNonNull(availableShares, "availableShares");
        }
    }
}
