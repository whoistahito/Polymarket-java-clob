package com.polymarket.builders;

import lombok.NonNull;

/** Builder API-key credentials, returned once on creation. All three parts are secret. */
public record BuilderCredentials(@NonNull String key, @NonNull String secret, @NonNull String passphrase) {


    @Override
    public String toString() {
        return "BuilderCredentials[key=***, secret=***, passphrase=***]";
    }
}
