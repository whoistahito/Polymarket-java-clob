package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A market's currently active reward programmes, with sponsor rates when the CLOB reports any. */
public record CurrentMarketRewards(
        String conditionId,
        ScoringRules scoring,
        List<RewardConfig> configs,
        Optional<BigDecimal> sponsoredDailyRate,
        Optional<Integer> sponsorsCount,
        Optional<BigDecimal> nativeDailyRate,
        Optional<BigDecimal> totalDailyRate) {

    public CurrentMarketRewards {
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(scoring, "scoring");
        configs = List.copyOf(Objects.requireNonNull(configs, "configs"));
        Objects.requireNonNull(sponsoredDailyRate, "sponsoredDailyRate");
        Objects.requireNonNull(sponsorsCount, "sponsorsCount");
        Objects.requireNonNull(nativeDailyRate, "nativeDailyRate");
        Objects.requireNonNull(totalDailyRate, "totalDailyRate");
    }
}
