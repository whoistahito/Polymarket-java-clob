package com.polymarket.rewards;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** What a maker earned on one day: per market, or with no condition when it is a daily total. */
public record UserEarning(Instant date, Optional<String> conditionId, String makerAddress,
        AssetEarning amount) {

    public UserEarning {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(makerAddress, "makerAddress");
        Objects.requireNonNull(amount, "amount");
    }
}
