package com.polymarket.authentication;

import lombok.NonNull;

/**
 * One API key returned by a key listing. It redacts itself so logging a listing cannot
 * disclose a credential; {@link #value()} is the deliberate way to read it.
 */
public record ApiKey(@NonNull String value) {

    public ApiKey {
        if (value.isBlank()) {
            throw new IllegalArgumentException("API key must not be blank");
        }
    }

    @Override
    public String toString() {
        return "ApiKey[value=***]";
    }
}
