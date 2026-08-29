package com.polymarket.markets;

import lombok.NonNull;

/** A Combo position. Position orders sign against Exchange V3. */
public record PositionId(@NonNull String value) implements AssetId {

    public PositionId {
        value = AssetIds.require(value, "position id");
    }
}
