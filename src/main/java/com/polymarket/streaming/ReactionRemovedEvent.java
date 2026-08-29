package com.polymarket.streaming;

import java.util.Optional;
import lombok.NonNull;

/** A removed reaction ({@code topic: "comments", type: "reaction_removed"}). */
public record ReactionRemovedEvent(@NonNull String id, @NonNull Optional<Long> commentId,
        @NonNull Optional<String> reactionType, @NonNull Optional<String> userAddress) {
}
