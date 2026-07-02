package com.polymarket.model.data;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Trade side for the Polymarket Data API ({@code https://data-api.polymarket.com}).
 *
 * <p>Mirrors the Rust SDK's {@code data::types::Side} enum.
 */
public enum DataSide {
    BUY,
    SELL;

    @JsonValue
    public String toJson() {
        return name();
    }
}
