package com.polymarket.rewards;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.NonNull;

/** One reward programme on a market: an asset paid out at a daily rate over a period. */
public record RewardConfig(
        @NonNull Optional<Long> id,
        @NonNull String assetAddress,
        @NonNull LocalDate startDate,
        Optional<LocalDate> endDate,
        @NonNull BigDecimal ratePerDay,
        Optional<BigDecimal> totalRewards,
        Optional<BigDecimal> remainingRewards,
        Optional<Integer> totalDays) {

}
