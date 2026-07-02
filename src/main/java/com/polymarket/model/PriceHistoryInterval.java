package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Time-window intervals for price history queries. Mirrors TS {@code PriceHistoryInterval}. */
public enum PriceHistoryInterval {

    MAX("max"),
    ONE_WEEK("1w"),
    ONE_DAY("1d"),
    SIX_HOURS("6h"),
    ONE_HOUR("1h");

    private final String value;

    PriceHistoryInterval(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
