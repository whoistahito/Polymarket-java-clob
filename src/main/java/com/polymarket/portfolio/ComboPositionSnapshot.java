package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * An ABSOLUTE Combo holding at {@code observedAt}, keyed by the Combo position id an RFQ
 * acceptance settles into. Shares are reported as the API sent them, zero included.
 */
public record ComboPositionSnapshot(
        @NonNull String comboConditionId,
        @NonNull String comboPositionId,
        @NonNull Optional<String> userAddress,
        @NonNull BigDecimal sharesBalance,
        @NonNull ComboStatus status,
        @NonNull Optional<BigDecimal> entryAveragePriceUsdc,
        @NonNull Optional<BigDecimal> entryCostUsdc,
        @NonNull Optional<BigDecimal> realizedPayoutUsdc,
        @NonNull Optional<BigDecimal> totalCostUsdc,
        @NonNull Optional<BigDecimal> grossEntryCostUsdc,
        @NonNull Optional<BigDecimal> entryFeesUsdc,
        @NonNull Optional<Instant> firstEntryAt,
        @NonNull Optional<Instant> resolvedAt,
        @NonNull Optional<Instant> updatedAt,
        @NonNull List<ComboLegSnapshot> legs,
        @NonNull Instant observedAt) {

    public ComboPositionSnapshot {
        legs = List.copyOf(legs);
    }
}
