package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Market creation ({@code event_type: "new_market"}); needs {@code custom_feature_enabled}. */
public record NewMarketEvent(@NonNull String id, @NonNull String question, @NonNull String market,
        @NonNull String slug, @NonNull Optional<String> description, @NonNull List<String> assetIds,
        @NonNull List<String> outcomes, @NonNull Optional<ParentEventInfo> parentEvent,
        @NonNull String timestamp, @NonNull List<String> tags,
        @NonNull Optional<String> conditionId, @NonNull Optional<Boolean> active,
        @NonNull List<String> clobTokenIds, @NonNull Optional<String> sportsMarketType,
        @NonNull Optional<String> line, @NonNull Optional<String> gameStartTime,
        @NonNull Optional<BigDecimal> orderPriceMinTickSize,
        @NonNull Optional<String> groupItemTitle) {

    public NewMarketEvent {
        assetIds = List.copyOf(assetIds);
        outcomes = List.copyOf(outcomes);
        tags = List.copyOf(tags);
        clobTokenIds = List.copyOf(clobTokenIds);
    }
}
