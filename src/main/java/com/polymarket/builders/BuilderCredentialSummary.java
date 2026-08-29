package com.polymarket.builders;

import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/** One entry from a builder API-key listing: an identifier and its lifecycle, never a secret. */
public record BuilderCredentialSummary(@NonNull String key, @NonNull Optional<Instant> createdAt,
        @NonNull Optional<Instant> revokedAt) {


    public boolean revoked() {
        return revokedAt.isPresent();
    }
}
