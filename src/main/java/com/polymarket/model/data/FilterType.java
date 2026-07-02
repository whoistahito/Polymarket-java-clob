package com.polymarket.model.data;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Filter type for trade size queries on the Polymarket Data API.
 *
 * <p>Both {@code filterType} and {@code filterAmount} must be provided together.
 * Mirrors the Rust SDK's {@code data::types::FilterType} enum.
 */
public enum FilterType {
    /** Filter by USDC cash value. */
    CASH,
    /** Filter by number of tokens. */
    TOKENS;

    @JsonValue
    public String toJson() {
        return name();
    }
}
