package com.polymarket.streaming;

import java.util.Optional;
import lombok.NonNull;

/** Parent-event metadata for grouped markets, nested in the custom market lifecycle events. */
public record ParentEventInfo(@NonNull Optional<String> id, @NonNull Optional<String> ticker,
        @NonNull Optional<String> slug, @NonNull Optional<String> title,
        @NonNull Optional<String> description) {
}
