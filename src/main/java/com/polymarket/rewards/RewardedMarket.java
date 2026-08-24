package com.polymarket.rewards;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;

/** A market carrying its reward programmes and the rules an order must score against. */
public record RewardedMarket(
        @NonNull String conditionId,
        @NonNull Optional<String> marketId,
        Optional<String> eventId,
        Optional<String> question,
        Optional<String> marketSlug,
        Optional<String> eventSlug,
        Optional<String> image,
        @NonNull ScoringRules scoring,
        @NonNull MarketMetrics metrics,
        List<RewardToken> tokens,
        List<RewardConfig> configs) {

    public RewardedMarket {
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        configs = List.copyOf(Objects.requireNonNull(configs, "configs"));
    }
}
