package com.polymarket.markets;

/** A Combo position. Position orders sign against Exchange V3. */
public record PositionId(String value) implements AssetId {

    public PositionId {
        value = AssetIds.require(value, "position id");
    }
}
