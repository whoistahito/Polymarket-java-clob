package com.polymarket.social;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One reaction left on a comment. */
public record Reaction(String id, Optional<String> commentId, Optional<String> reactionType,
        Optional<String> icon, Optional<String> userAddress, Optional<Instant> createdAt) {

    public Reaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(reactionType, "reactionType");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(userAddress, "userAddress");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
