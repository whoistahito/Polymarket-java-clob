package com.polymarket.streaming;

import java.util.List;
import java.util.Optional;

/** Market resolution ({@code event_type: "market_resolved"}); needs {@code custom_feature_enabled}. */
public record MarketResolvedEvent(
        String id,
        String market,
        List<String> assetIds,
        String winningAssetId,
        String winningOutcome,
        Optional<ParentEventInfo> parentEvent,
        String timestamp,
        List<String> tags) {

    public MarketResolvedEvent {
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        parentEvent = parentEvent == null ? Optional.empty() : parentEvent;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
