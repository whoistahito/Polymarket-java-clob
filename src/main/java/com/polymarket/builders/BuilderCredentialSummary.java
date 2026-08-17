package com.polymarket.builders;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One entry from a builder API-key listing: an identifier and its lifecycle, never a secret. */
public record BuilderCredentialSummary(
        String key, Optional<Instant> createdAt, Optional<Instant> revokedAt) {

    public BuilderCredentialSummary {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(revokedAt, "revokedAt");
    }

    public boolean revoked() {
        return revokedAt.isPresent();
    }
}
