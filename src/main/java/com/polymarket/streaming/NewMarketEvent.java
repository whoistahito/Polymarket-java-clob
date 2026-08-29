package com.polymarket.streaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Market creation ({@code event_type: "new_market"}); needs {@code custom_feature_enabled}. */
public record NewMarketEvent(
        String id,
        String question,
        String market,
        String slug,
        Optional<String> description,
        List<String> assetIds,
        List<String> outcomes,
        Optional<ParentEventInfo> parentEvent,
        String timestamp,
        List<String> tags,
        Optional<String> conditionId,
        Optional<Boolean> active,
        List<String> clobTokenIds,
        Optional<String> sportsMarketType,
        Optional<String> line,
        Optional<String> gameStartTime,
        Optional<BigDecimal> orderPriceMinTickSize,
        Optional<String> groupItemTitle) {

    public NewMarketEvent {
        description = description == null ? Optional.empty() : description;
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        parentEvent = parentEvent == null ? Optional.empty() : parentEvent;
        tags = tags == null ? List.of() : List.copyOf(tags);
        conditionId = conditionId == null ? Optional.empty() : conditionId;
        active = active == null ? Optional.empty() : active;
        clobTokenIds = clobTokenIds == null ? List.of() : List.copyOf(clobTokenIds);
        sportsMarketType = sportsMarketType == null ? Optional.empty() : sportsMarketType;
        line = line == null ? Optional.empty() : line;
        gameStartTime = gameStartTime == null ? Optional.empty() : gameStartTime;
        orderPriceMinTickSize = orderPriceMinTickSize == null ? Optional.empty() : orderPriceMinTickSize;
        groupItemTitle = groupItemTitle == null ? Optional.empty() : groupItemTitle;
    }
}
