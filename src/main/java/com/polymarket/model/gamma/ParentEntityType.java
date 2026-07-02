package com.polymarket.model.gamma;

public enum ParentEntityType {
    EVENT("Event"), SERIES("Series"), MARKET("market");
    private final String value;
    ParentEntityType(String value) { this.value = value; }
    public String getValue() { return value; }
}
