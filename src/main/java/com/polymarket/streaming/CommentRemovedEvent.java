package com.polymarket.streaming;

import java.util.Optional;

/** A deleted comment ({@code topic: "comments", type: "comment_removed"}). */
public record CommentRemovedEvent(
        String id,
        Optional<String> body,
        Optional<RtdsEntityType> parentEntityType,
        Optional<Long> parentEntityId,
        Optional<String> userAddress) {

    public CommentRemovedEvent {
        body = body == null ? Optional.empty() : body;
        parentEntityType = parentEntityType == null ? Optional.empty() : parentEntityType;
        parentEntityId = parentEntityId == null ? Optional.empty() : parentEntityId;
        userAddress = userAddress == null ? Optional.empty() : userAddress;
    }
}
