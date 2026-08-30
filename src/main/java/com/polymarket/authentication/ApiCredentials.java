package com.polymarket.authentication;

import lombok.NonNull;

/**
 * L2 API Credentials, separate from any Account Signer key. All three parts are secret, and a
 * blank part cannot exist: half-built credentials would authenticate nothing.
 */
public record ApiCredentials(@NonNull String key, @NonNull String secret,
        @NonNull String passphrase) {

    public ApiCredentials {
        requirePresent(key, "key");
        requirePresent(secret, "secret");
        requirePresent(passphrase, "passphrase");
    }

    private static void requirePresent(String value, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("API credential " + field + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "ApiCredentials[key=***, secret=***, passphrase=***]";
    }
}
