package com.polymarket.portfolio;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One unread notification. Owner is empty text on a broadcast and absent when never sent. */
public record Notification(
        long id,
        Optional<String> owner,
        NotificationKind kind,
        NotificationPayload payload,
        Instant createdAt) {

    public Notification {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
