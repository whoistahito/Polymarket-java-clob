package com.polymarket.streaming;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Market resolution ({@code event_type: "market_resolved"}); needs {@code custom_feature_enabled}. */
public record MarketResolvedEvent(@NonNull String id, @NonNull String market,
        @NonNull List<String> assetIds, @NonNull String winningAssetId,
        @NonNull String winningOutcome, @NonNull Optional<ParentEventInfo> parentEvent,
        @NonNull String timestamp, @NonNull List<String> tags) {

    public MarketResolvedEvent {
        assetIds = List.copyOf(assetIds);
        tags = List.copyOf(tags);
    }
}
