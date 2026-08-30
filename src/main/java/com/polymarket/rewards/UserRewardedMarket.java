package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A rewarded market seen through one maker's live share of it. */
public record UserRewardedMarket(@NonNull RewardedMarket market,
        @NonNull Optional<String> makerAddress, @NonNull Optional<BigDecimal> earningPercentage,
        @NonNull List<AssetEarning> earnings) {

    public UserRewardedMarket {
        earnings = List.copyOf(earnings);
    }
}
