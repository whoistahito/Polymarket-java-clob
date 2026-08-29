package com.polymarket.streaming;

import java.util.Optional;
import lombok.NonNull;

/** A new reaction on a comment ({@code topic: "comments", type: "reaction_created"}). */
public record ReactionCreatedEvent(@NonNull String id, @NonNull Optional<Long> commentId,
        @NonNull Optional<String> reactionType, @NonNull Optional<String> icon,
        @NonNull Optional<String> userAddress, @NonNull Optional<String> createdAt) {
}
