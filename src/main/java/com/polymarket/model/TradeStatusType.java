package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed trade status values returned by the Polymarket CLOB API.
 *
 * <p>Mirrors the Rust {@code TradeStatusType} enum in
 * {@code rs-clob-client/src/clob/types/mod.rs}. Unrecognised values are
 * mapped to {@link #UNKNOWN} rather than throwing.
 */
public enum TradeStatusType {

    MATCHED,
    MINED,
    CONFIRMED,
    RETRYING,
    FAILED,

    /** Fallback for status strings not recognised by this SDK version. */
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
    public static TradeStatusType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return TradeStatusType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
