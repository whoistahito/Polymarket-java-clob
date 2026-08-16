package com.polymarket.markets;

/** A CLOB outcome token. Token orders sign against Exchange V2. */
public record TokenId(String value) implements AssetId {

    public TokenId {
        value = AssetIds.require(value, "token id");
    }
}
