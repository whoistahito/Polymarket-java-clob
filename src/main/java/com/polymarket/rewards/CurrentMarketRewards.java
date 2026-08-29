package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A market's currently active reward programmes, with sponsor rates when the CLOB reports any. */
public record CurrentMarketRewards(@NonNull String conditionId, @NonNull ScoringRules scoring,
        @NonNull List<RewardConfig> configs, @NonNull Optional<BigDecimal> sponsoredDailyRate,
        @NonNull Optional<Integer> sponsorsCount, @NonNull Optional<BigDecimal> nativeDailyRate,
        @NonNull Optional<BigDecimal> totalDailyRate) {

    public CurrentMarketRewards {
        configs = List.copyOf(configs);
    }
}
