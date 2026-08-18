package com.polymarket.streaming;

import java.util.Optional;

/** A new comment ({@code topic: "comments", type: "comment_created"}). */
public record CommentCreatedEvent(
        String id,
        Optional<String> body,
        Optional<RtdsEntityType> parentEntityType,
        Optional<Long> parentEntityId,
        Optional<String> parentCommentId,
        Optional<String> userAddress,
        Optional<String> replyAddress,
        Optional<String> createdAt,
        Optional<Long> reactionCount,
        Optional<Long> reportCount,
        Optional<CommentProfile> profile) {

    public CommentCreatedEvent {
        body = body == null ? Optional.empty() : body;
        parentEntityType = parentEntityType == null ? Optional.empty() : parentEntityType;
        parentEntityId = parentEntityId == null ? Optional.empty() : parentEntityId;
        parentCommentId = parentCommentId == null ? Optional.empty() : parentCommentId;
        userAddress = userAddress == null ? Optional.empty() : userAddress;
        replyAddress = replyAddress == null ? Optional.empty() : replyAddress;
        createdAt = createdAt == null ? Optional.empty() : createdAt;
        reactionCount = reactionCount == null ? Optional.empty() : reactionCount;
        reportCount = reportCount == null ? Optional.empty() : reportCount;
        profile = profile == null ? Optional.empty() : profile;
    }
}
