package com.polymarket.social;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A comment on an event, market or series, as Gamma publishes it. */
public record Comment(
        String id,
        Optional<String> body,
        Optional<ParentEntityType> parentEntityType,
        Optional<String> parentEntityId,
        Optional<String> parentCommentId,
        Optional<String> userAddress,
        Optional<String> replyAddress,
        Optional<Instant> createdAt,
        Optional<Instant> updatedAt,
        Optional<CommentAuthor> author,
        List<Reaction> reactions,
        Optional<Integer> reportCount,
        Optional<Integer> reactionCount) {

    public Comment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(parentEntityType, "parentEntityType");
        Objects.requireNonNull(parentEntityId, "parentEntityId");
        Objects.requireNonNull(parentCommentId, "parentCommentId");
        Objects.requireNonNull(userAddress, "userAddress");
        Objects.requireNonNull(replyAddress, "replyAddress");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(reportCount, "reportCount");
        Objects.requireNonNull(reactionCount, "reactionCount");
        reactions = List.copyOf(reactions);
    }
}
