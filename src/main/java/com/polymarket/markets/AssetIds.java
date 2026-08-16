package com.polymarket.markets;

/** Shared validation for the protocol's uint256 asset identifiers. */
final class AssetIds {

    private AssetIds() {
    }

    static String require(String value, String field) {
        if (value == null || !value.matches("[0-9]+") || value.matches("0+")) {
            throw new IllegalArgumentException(
                    field + " must be a positive decimal integer, got: " + value);
        }
        return value;
    }
}
