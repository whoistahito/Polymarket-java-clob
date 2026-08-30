package com.polymarket.rewards;

import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/** What a maker earned on one day: per market, or with no condition when it is a daily total. */
public record UserEarning(@NonNull Instant date, @NonNull Optional<String> conditionId, @NonNull String makerAddress,
        @NonNull AssetEarning amount) {

}
