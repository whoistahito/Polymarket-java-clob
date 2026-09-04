package com.polymarket.streaming;

import java.util.Locale;

/** RTDS {@code comments} topic event types; each is its own subscription entry, never a wildcard. */
public enum CommentEventType {
    COMMENT_CREATED, COMMENT_REMOVED, REACTION_CREATED, REACTION_REMOVED;

    /** The snake_case wire value, e.g. {@code "comment_created"}. */
    public String wireValue() {
        // Locale.ROOT: a Turkish default would send "reactıon_created" and the topic would never match.
        return name().toLowerCase(Locale.ROOT);
    }
}
