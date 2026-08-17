package com.polymarket.builders;

import java.util.Objects;

/** Builder API-key credentials, returned once on creation. All three parts are secret. */
public record BuilderCredentials(String key, String secret, String passphrase) {

    public BuilderCredentials {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(passphrase, "passphrase");
    }

    @Override
    public String toString() {
        return "BuilderCredentials[key=***, secret=***, passphrase=***]";
    }
}
