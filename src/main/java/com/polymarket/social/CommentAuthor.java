package com.polymarket.social;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The commenter's profile as embedded in a comment; a lighter shape than {@link Profile}. */
public record CommentAuthor(
        Optional<String> name,
        Optional<String> pseudonym,
        Optional<Boolean> displayUsernamePublic,
        Optional<String> bio,
        Optional<Boolean> moderator,
        Optional<Boolean> creator,
        Optional<String> proxyWallet,
        Optional<String> baseAddress,
        Optional<String> profileImage,
        List<CommentPosition> positions) {

    public CommentAuthor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pseudonym, "pseudonym");
        Objects.requireNonNull(displayUsernamePublic, "displayUsernamePublic");
        Objects.requireNonNull(bio, "bio");
        Objects.requireNonNull(moderator, "moderator");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(proxyWallet, "proxyWallet");
        Objects.requireNonNull(baseAddress, "baseAddress");
        Objects.requireNonNull(profileImage, "profileImage");
        positions = List.copyOf(positions);
    }
}
