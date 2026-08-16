package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** An outcome token on a rewarded market, with its price when the read carried one. */
public record RewardToken(String tokenId, String outcome, Optional<BigDecimal> price) {

    public RewardToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(price, "price");
    }
}
