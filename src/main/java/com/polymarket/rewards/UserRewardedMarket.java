package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;

/** A rewarded market seen through one maker's live share of it. */
public record UserRewardedMarket(@NonNull RewardedMarket market, @NonNull Optional<String> makerAddress,
        Optional<BigDecimal> earningPercentage, List<AssetEarning> earnings) {

    public UserRewardedMarket {
        earnings = List.copyOf(Objects.requireNonNull(earnings, "earnings"));
    }
}
