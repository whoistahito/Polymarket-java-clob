package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** An amount earned in one reward asset, with that asset's exchange rate when published. */
public record AssetEarning(String assetAddress, BigDecimal earnings,
        Optional<BigDecimal> assetRate) {

    public AssetEarning {
        Objects.requireNonNull(assetAddress, "assetAddress");
        Objects.requireNonNull(earnings, "earnings");
        Objects.requireNonNull(assetRate, "assetRate");
    }
}
