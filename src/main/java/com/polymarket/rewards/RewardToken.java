package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** An outcome token on a rewarded market, with its price when the read carried one. */
public record RewardToken(@NonNull String tokenId, @NonNull String outcome, @NonNull Optional<BigDecimal> price) {

}
