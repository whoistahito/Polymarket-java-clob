package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/** One leg of a Combo position, with the leg market's own live resolution state. */
public record ComboLegSnapshot(
        int index,
        @NonNull String positionId,
        @NonNull Optional<String> conditionId,
        @NonNull Optional<Integer> outcomeIndex,
        @NonNull Optional<String> outcomeLabel,
        @NonNull ComboStatus status,
        @NonNull Optional<Instant> resolvedAt,
        @NonNull Optional<BigDecimal> currentPrice) {
}
