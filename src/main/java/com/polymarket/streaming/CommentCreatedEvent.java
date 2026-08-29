package com.polymarket.streaming;

import java.util.Optional;
import lombok.NonNull;

/** A new comment ({@code topic: "comments", type: "comment_created"}). */
public record CommentCreatedEvent(@NonNull String id, @NonNull Optional<String> body,
        @NonNull Optional<RtdsEntityType> parentEntityType, @NonNull Optional<Long> parentEntityId,
        @NonNull Optional<String> parentCommentId, @NonNull Optional<String> userAddress,
        @NonNull Optional<String> replyAddress, @NonNull Optional<String> createdAt,
        @NonNull Optional<Long> reactionCount, @NonNull Optional<Long> reportCount,
        @NonNull Optional<CommentProfile> profile) {
}
