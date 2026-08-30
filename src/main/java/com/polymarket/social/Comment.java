package com.polymarket.social;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * A comment on an event, market or series, as Gamma publishes it. {@code parentEntityTypeText}
 * keeps the wire value, so a kind Gamma has not documented yet is still readable.
 */
public record Comment(
        @NonNull String id,
        @NonNull Optional<String> body,
        @NonNull Optional<ParentEntityType> parentEntityType,
        @NonNull Optional<String> parentEntityTypeText,
        @NonNull Optional<String> parentEntityId,
        @NonNull Optional<String> parentCommentId,
        @NonNull Optional<String> userAddress,
        @NonNull Optional<String> replyAddress,
        @NonNull Optional<Instant> createdAt,
        @NonNull Optional<Instant> updatedAt,
        @NonNull Optional<CommentAuthor> author,
        @NonNull List<Reaction> reactions,
        @NonNull Optional<Integer> reportCount,
        @NonNull Optional<Integer> reactionCount) {

    public Comment {
        reactions = List.copyOf(reactions);
    }
}
