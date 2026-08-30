package com.polymarket.social;

/** The kind of entity a comment or search hit is attached to. Wire values are Gamma's own, not uniformly cased. */
public enum ParentEntityType {
    EVENT("Event"), SERIES("Series"), MARKET("market");

    private final String wireValue;

    ParentEntityType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
