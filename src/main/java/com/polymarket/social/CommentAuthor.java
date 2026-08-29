package com.polymarket.social;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** The commenter's profile as embedded in a comment; a lighter shape than {@link Profile}. */
public record CommentAuthor(
        @NonNull Optional<String> name,
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
        positions = List.copyOf(positions);
    }
}
