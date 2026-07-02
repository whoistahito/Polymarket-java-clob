package com.polymarket.client;

import java.util.Objects;

/**
 * API credentials for Polymarket L2 authentication.
 * These are returned when creating or deriving an API key.
 */
public final class ApiKeyCreds {

    private final String key;
    private final String secret;
    private final String passphrase;

    public ApiKeyCreds(String key, String secret, String passphrase) {
        this.key = Objects.requireNonNull(key, "key");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.passphrase = Objects.requireNonNull(passphrase, "passphrase");
    }

    public String getKey() {
        return key;
    }

    public String getSecret() {
        return secret;
    }

    public String getPassphrase() {
        return passphrase;
    }

    @Override
    public String toString() {
        return "ApiKeyCreds{key='" + key + "', passphrase='" + passphrase + "', secret=***}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKeyCreds that = (ApiKeyCreds) o;
        return key.equals(that.key) && secret.equals(that.secret) && passphrase.equals(that.passphrase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, secret, passphrase);
    }
}
