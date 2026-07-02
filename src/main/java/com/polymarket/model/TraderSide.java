package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Indicates whether a fill was as taker or maker in a trade.
 *
 * <p>Mirrors the Rust {@code TraderSide} enum in
 * {@code rs-clob-client/src/clob/types/mod.rs}. The TypeScript SDK uses
 * the string union {@code "TAKER" | "MAKER"} in {@code types.ts}.
 * Unrecognised values are mapped to {@link #UNKNOWN} rather than throwing.
 */
public enum TraderSide {

    TAKER,
    MAKER,

    /** Fallback for values not recognised by this SDK version. */
    UNKNOWN;

    /** Serialize as the enum name (uppercase). */
    @JsonValue
    public String toValue() {
        return name();
    }

    /**
     * Jackson / public factory. Matches case-insensitively; unrecognised values
     * map to {@link #UNKNOWN} rather than throwing.
     */
    @JsonCreator
    public static TraderSide fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return TraderSide.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
