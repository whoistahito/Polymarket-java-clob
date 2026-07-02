package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typed order status values returned by the Polymarket CLOB API.
 *
 * <p>Mirrors the Rust {@code OrderStatusType} enum in
 * {@code rs-clob-client/src/clob/types/mod.rs}. Unknown values from the API
 * are mapped to {@link #UNKNOWN} rather than throwing, preserving forward
 * compatibility with new statuses introduced server-side.
 */
public enum OrderStatusType {

    LIVE,
    MATCHED,
    CANCELED,
    DELAYED,
    UNMATCHED,

    /**
     * Fallback for status strings not recognised by this SDK version.
     * Use {@code orderStatus == OrderStatusType.UNKNOWN} to detect unrecognised values.
     */
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
    public static OrderStatusType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return OrderStatusType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
