package com.polymarket.rewards;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.NonNull;

/** One reward programme on a market: an asset paid out at a daily rate over a period. */
public record RewardConfig(@NonNull Optional<Long> id, @NonNull String assetAddress,
        @NonNull LocalDate startDate, @NonNull Optional<LocalDate> endDate,
        @NonNull BigDecimal ratePerDay, @NonNull Optional<BigDecimal> totalRewards,
        @NonNull Optional<BigDecimal> remainingRewards, @NonNull Optional<Integer> totalDays) {

}
