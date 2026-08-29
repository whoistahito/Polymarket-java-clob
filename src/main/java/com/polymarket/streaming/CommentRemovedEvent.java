package com.polymarket.streaming;

import java.util.Optional;
import lombok.NonNull;

/** A deleted comment ({@code topic: "comments", type: "comment_removed"}). */
public record CommentRemovedEvent(@NonNull String id, @NonNull Optional<String> body,
        @NonNull Optional<RtdsEntityType> parentEntityType, @NonNull Optional<Long> parentEntityId,
        @NonNull Optional<String> userAddress) {
}
