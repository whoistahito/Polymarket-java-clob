package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported EVM chain IDs for Polymarket.
 *
 * <p>Mirrors the TypeScript {@code Chain} enum in {@code clob-client/src/types.ts}.
 */
public enum Chain {

    /** Polygon Mainnet. */
    POLYGON(137),

    /** Polygon Amoy Testnet. */
    AMOY(80002);

    private final int id;

    Chain(int id) {
        this.id = id;
    }

    /** Returns the numeric chain ID. */
    @JsonValue
    public int getId() {
        return id;
    }

    /**
     * Looks up a {@code Chain} by its numeric ID.
     *
     * @param id the chain ID (e.g. 137)
     * @return the matching {@code Chain}
     * @throws IllegalArgumentException if the ID is not recognised
     */
    @JsonCreator
    public static Chain fromId(int id) {
        for (Chain c : values()) {
            if (c.id == id) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown chain ID: " + id);
    }
}
