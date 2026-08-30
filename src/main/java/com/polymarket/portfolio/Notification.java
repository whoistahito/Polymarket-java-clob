package com.polymarket.portfolio;

import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;

/** One unread notification. Owner is empty text on a broadcast and absent when never sent. */
public record Notification(
        long id,
        @NonNull Optional<String> owner,
        @NonNull NotificationKind kind,
        @NonNull NotificationPayload payload,
        @NonNull Instant createdAt) {
}
