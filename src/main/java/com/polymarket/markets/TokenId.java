package com.polymarket.markets;

import lombok.NonNull;

/** A CLOB outcome token. Token orders sign against Exchange V2. */
public record TokenId(@NonNull String value) implements AssetId {

    public TokenId {
        value = AssetIds.require(value, "token id");
    }
}
