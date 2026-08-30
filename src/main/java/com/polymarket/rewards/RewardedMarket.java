package com.polymarket.rewards;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A market carrying its reward programmes and the rules an order must score against. */
public record RewardedMarket(@NonNull String conditionId, @NonNull Optional<String> marketId,
        @NonNull Optional<String> eventId, @NonNull Optional<String> question,
        @NonNull Optional<String> marketSlug, @NonNull Optional<String> eventSlug,
        @NonNull Optional<String> image, @NonNull ScoringRules scoring,
        @NonNull MarketMetrics metrics, @NonNull List<RewardToken> tokens,
        @NonNull List<RewardConfig> configs) {

    public RewardedMarket {
        tokens = List.copyOf(tokens);
        configs = List.copyOf(configs);
    }
}
