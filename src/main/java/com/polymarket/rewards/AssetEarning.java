package com.polymarket.rewards;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.NonNull;

/** An amount earned in one reward asset, with that asset's exchange rate when published. */
public record AssetEarning(@NonNull String assetAddress, @NonNull BigDecimal earnings,
        @NonNull Optional<BigDecimal> assetRate) {

}
