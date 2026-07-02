package com.polymarket.model.gamma;

public enum RelatedTagsStatus {
    ACTIVE("active"), CLOSED("closed"), ALL("all");
    private final String value;
    RelatedTagsStatus(String value) { this.value = value; }
    public String getValue() { return value; }
}
