package com.polymarket.streaming;

import java.util.Optional;

/** A new reaction on a comment ({@code topic: "comments", type: "reaction_created"}). */
public record ReactionCreatedEvent(
        String id,
        Optional<Long> commentId,
        Optional<String> reactionType,
        Optional<String> icon,
        Optional<String> userAddress,
        Optional<String> createdAt) {

    public ReactionCreatedEvent {
        commentId = commentId == null ? Optional.empty() : commentId;
        reactionType = reactionType == null ? Optional.empty() : reactionType;
        icon = icon == null ? Optional.empty() : icon;
        userAddress = userAddress == null ? Optional.empty() : userAddress;
        createdAt = createdAt == null ? Optional.empty() : createdAt;
    }
}
