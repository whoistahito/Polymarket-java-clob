package com.polymarket.rewards;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A market carrying its reward programmes and the rules an order must score against. */
public record RewardedMarket(
        String conditionId,
        Optional<String> marketId,
        Optional<String> eventId,
        Optional<String> question,
        Optional<String> marketSlug,
        Optional<String> eventSlug,
        Optional<String> image,
        ScoringRules scoring,
        MarketMetrics metrics,
        List<RewardToken> tokens,
        List<RewardConfig> configs) {

    public RewardedMarket {
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(marketSlug, "marketSlug");
        Objects.requireNonNull(eventSlug, "eventSlug");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(scoring, "scoring");
        Objects.requireNonNull(metrics, "metrics");
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        configs = List.copyOf(Objects.requireNonNull(configs, "configs"));
    }
}
