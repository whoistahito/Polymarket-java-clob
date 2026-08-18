package com.polymarket.streaming;

import java.util.Objects;
import java.util.Optional;

/** One {@code comments} subscription entry: an event type plus an optional official entity filter. */
public record CommentSubscription(
        CommentEventType type, Optional<RtdsEntityType> entityType, Optional<Long> entityId) {

    public CommentSubscription {
        Objects.requireNonNull(type, "type");
        entityType = entityType == null ? Optional.empty() : entityType;
        entityId = entityId == null ? Optional.empty() : entityId;
        if (entityType.isPresent() != entityId.isPresent()) {
            throw new IllegalArgumentException("entityType and entityId must travel together");
        }
    }

    public static CommentSubscription all(CommentEventType type) {
        return new CommentSubscription(type, Optional.empty(), Optional.empty());
    }

    public static CommentSubscription forEntity(CommentEventType type, RtdsEntityType entityType, long entityId) {
        return new CommentSubscription(type, Optional.of(entityType), Optional.of(entityId));
    }
}
