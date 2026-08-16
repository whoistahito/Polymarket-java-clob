package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A rewarded market seen through one maker's live share of it. */
public record UserRewardedMarket(RewardedMarket market, Optional<String> makerAddress,
        Optional<BigDecimal> earningPercentage, List<AssetEarning> earnings) {

    public UserRewardedMarket {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(makerAddress, "makerAddress");
        Objects.requireNonNull(earningPercentage, "earningPercentage");
        earnings = List.copyOf(Objects.requireNonNull(earnings, "earnings"));
    }
}
