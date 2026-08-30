package com.polymarket.social;

import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/** One reaction left on a comment, including the reacting profile Gamma nests when it has one. */
public record Reaction(
        @NonNull String id,
        @NonNull Optional<String> commentId,
        @NonNull Optional<String> reactionType,
        @NonNull Optional<String> icon,
        @NonNull Optional<String> userAddress,
        @NonNull Optional<Instant> createdAt,
        @NonNull Optional<CommentAuthor> author) {
}
