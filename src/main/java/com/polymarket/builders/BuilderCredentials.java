package com.polymarket.builders;

import lombok.NonNull;

/** Builder API-key credentials, returned once on creation. All three parts are secret. */
public record BuilderCredentials(@NonNull String key, @NonNull String secret, @NonNull String passphrase) {

    /** A blank part is not a credential: half of one would fail later, at signing time. */
    public BuilderCredentials {
        if (key.isBlank() || secret.isBlank() || passphrase.isBlank()) {
            // Named, never quoted: the parts stay redacted even in the failure.
            throw new IllegalArgumentException("builder credentials need a key, secret and passphrase");
        }
    }

    @Override
    public String toString() {
        return "BuilderCredentials[key=***, secret=***, passphrase=***]";
    }
}
