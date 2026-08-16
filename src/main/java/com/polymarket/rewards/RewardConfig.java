package com.polymarket.rewards;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** One reward programme on a market: an asset paid out at a daily rate over a period. */
public record RewardConfig(
        Optional<Long> id,
        String assetAddress,
        LocalDate startDate,
        Optional<LocalDate> endDate,
        BigDecimal ratePerDay,
        Optional<BigDecimal> totalRewards,
        Optional<BigDecimal> remainingRewards,
        Optional<Integer> totalDays) {

    public RewardConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetAddress, "assetAddress");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(ratePerDay, "ratePerDay");
        Objects.requireNonNull(totalRewards, "totalRewards");
        Objects.requireNonNull(remainingRewards, "remainingRewards");
        Objects.requireNonNull(totalDays, "totalDays");
    }
}
