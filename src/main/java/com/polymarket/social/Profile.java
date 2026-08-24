package com.polymarket.social;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** A public Polymarket profile, as Gamma's {@code public-profile} endpoint publishes it. */
public record Profile(
        @NonNull Optional<String> proxyWallet,
        Optional<String> name,
        Optional<String> pseudonym,
        Optional<String> bio,
        Optional<String> profileImage,
        Optional<Boolean> displayUsernamePublic,
        Optional<Boolean> verifiedBadge,
        Optional<String> xUsername,
        Optional<Instant> createdAt,
        List<LinkedAccount> users) {

    public Profile {
        users = List.copyOf(users);
    }
}
