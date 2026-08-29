package com.polymarket.social;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** The commenter's profile as embedded in a comment; a lighter shape than {@link Profile}. */
public record CommentAuthor(@NonNull Optional<String> name, @NonNull Optional<String> pseudonym,
        @NonNull Optional<Boolean> displayUsernamePublic, @NonNull Optional<String> bio,
        @NonNull Optional<Boolean> moderator, @NonNull Optional<Boolean> creator,
        @NonNull Optional<String> proxyWallet, @NonNull Optional<String> baseAddress,
        @NonNull Optional<String> profileImage, @NonNull List<CommentPosition> positions) {

    public CommentAuthor {
        positions = List.copyOf(positions);
    }
}
