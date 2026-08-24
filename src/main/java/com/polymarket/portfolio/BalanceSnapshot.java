package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;

/**
 * An exact balance and its spender allowances at {@code observedAt}. Wire values are 6-decimal
 * fixed math (clob-openapi.yaml BalanceAllowanceResponse; pUSD has 6 decimals), scaled here
 * without rounding.
 */
public record BalanceSnapshot(
        @NonNull AssetType assetType,
        @NonNull Optional<String> tokenId,
        @NonNull BigDecimal balance,
        @NonNull Map<String, BigDecimal> allowances,
        @NonNull Instant observedAt) {

    public BalanceSnapshot {
        allowances = Map.copyOf(allowances);
    }
}
