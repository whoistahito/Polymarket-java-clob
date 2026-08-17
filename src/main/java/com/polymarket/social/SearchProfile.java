package com.polymarket.social;

import java.util.Objects;
import java.util.Optional;

/** One profile matched by a search query; a lighter shape than {@link Profile}. */
public record SearchProfile(
        Optional<String> id,
        Optional<String> name,
        Optional<String> pseudonym,
        Optional<Boolean> displayUsernamePublic,
        Optional<String> profileImage,
        Optional<String> bio,
        Optional<String> proxyWallet,
        Optional<Boolean> walletActivated,
        Optional<Boolean> closeOnly) {

    public SearchProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pseudonym, "pseudonym");
        Objects.requireNonNull(displayUsernamePublic, "displayUsernamePublic");
        Objects.requireNonNull(profileImage, "profileImage");
        Objects.requireNonNull(bio, "bio");
        Objects.requireNonNull(proxyWallet, "proxyWallet");
        Objects.requireNonNull(walletActivated, "walletActivated");
        Objects.requireNonNull(closeOnly, "closeOnly");
    }
}
