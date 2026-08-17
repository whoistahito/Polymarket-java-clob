package com.polymarket.social;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A public Polymarket profile, as Gamma's {@code public-profile} endpoint publishes it. */
public record Profile(
        Optional<String> proxyWallet,
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
        Objects.requireNonNull(proxyWallet, "proxyWallet");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pseudonym, "pseudonym");
        Objects.requireNonNull(bio, "bio");
        Objects.requireNonNull(profileImage, "profileImage");
        Objects.requireNonNull(displayUsernamePublic, "displayUsernamePublic");
        Objects.requireNonNull(verifiedBadge, "verifiedBadge");
        Objects.requireNonNull(xUsername, "xUsername");
        Objects.requireNonNull(createdAt, "createdAt");
        users = List.copyOf(users);
    }
}
