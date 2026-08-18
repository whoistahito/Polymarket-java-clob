package com.polymarket.streaming;

import java.util.Optional;

/** A removed reaction ({@code topic: "comments", type: "reaction_removed"}). */
public record ReactionRemovedEvent(
        String id, Optional<Long> commentId, Optional<String> reactionType, Optional<String> userAddress) {

    public ReactionRemovedEvent {
        commentId = commentId == null ? Optional.empty() : commentId;
        reactionType = reactionType == null ? Optional.empty() : reactionType;
        userAddress = userAddress == null ? Optional.empty() : userAddress;
    }
}
