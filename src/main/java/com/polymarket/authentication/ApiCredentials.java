package com.polymarket.authentication;

import java.util.Objects;

/** L2 API credentials, separate from local signing authority. All three parts are secret. */
public record ApiCredentials(String key, String secret, String passphrase) {

    public ApiCredentials {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(passphrase, "passphrase");
    }

    @Override
    public String toString() {
        return "ApiCredentials[key=***, secret=***, passphrase=***]";
    }
}
