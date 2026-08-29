package com.polymarket.social;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A public Polymarket profile, as Gamma's {@code public-profile} endpoint publishes it. */
public record Profile(@NonNull Optional<String> proxyWallet, @NonNull Optional<String> name,
        @NonNull Optional<String> pseudonym, @NonNull Optional<String> bio,
        @NonNull Optional<String> profileImage, @NonNull Optional<Boolean> displayUsernamePublic,
        @NonNull Optional<Boolean> verifiedBadge, @NonNull Optional<String> xUsername,
        @NonNull Optional<Instant> createdAt, @NonNull List<LinkedAccount> users) {

    public Profile {
        users = List.copyOf(users);
    }
}
