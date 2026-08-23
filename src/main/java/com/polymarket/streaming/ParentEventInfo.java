package com.polymarket.streaming;

import java.util.Optional;

/** Parent-event metadata for grouped markets, nested in the custom market lifecycle events. */
public record ParentEventInfo(
        Optional<String> id,
        Optional<String> ticker,
        Optional<String> slug,
        Optional<String> title,
        Optional<String> description) {

    public ParentEventInfo {
        id = id == null ? Optional.empty() : id;
        ticker = ticker == null ? Optional.empty() : ticker;
        slug = slug == null ? Optional.empty() : slug;
        title = title == null ? Optional.empty() : title;
        description = description == null ? Optional.empty() : description;
    }
}
