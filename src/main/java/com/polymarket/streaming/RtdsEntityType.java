package com.polymarket.streaming;

import java.util.Optional;

/** Entity kinds the RTDS {@code comments} filter documents: {@code parentEntityType} is title-cased. */
public enum RtdsEntityType {
    EVENT("Event"), MARKET("Market");

    private final String wireValue;

    RtdsEntityType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    /** Matches an inbound {@code parentEntityType} value; unrecognised values map to empty. */
    public static Optional<RtdsEntityType> fromWireValue(String wireValue) {
        for (RtdsEntityType type : values()) {
            if (type.wireValue.equalsIgnoreCase(wireValue)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
