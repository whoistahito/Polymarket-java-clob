package com.polymarket.markets;

import java.math.BigInteger;

/** Shared validation for the protocol's uint256 asset identifiers. */
final class AssetIds {

    private static final BigInteger UPPER_BOUND = BigInteger.ONE.shiftLeft(256);

    private AssetIds() {
    }

    static String require(String value, String field) {
        if (value == null || !value.matches("[0-9]+")
                || new BigInteger(value).compareTo(UPPER_BOUND) >= 0) {
            throw new IllegalArgumentException(
                    field + " must be an unsigned 256-bit integer, got: " + value);
        }
        return value;
    }
}
