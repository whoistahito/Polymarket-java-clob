package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Match type for RFQ orders. Mirrors TS {@code RfqMatchType}. */
public enum RfqMatchType {

    COMPLEMENTARY("COMPLEMENTARY"),
    MERGE("MERGE"),
    MINT("MINT");

    private final String value;

    RfqMatchType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
