package com.polymarket.rtds;

/**
 * Comment event types for the {@code comments} RTDS topic.
 *
 * <p>Mirrors the Rust SDK {@code CommentType}. The {@link #wireValue()} is the
 * snake_case string sent as the subscription {@code type} field.
 */
public enum CommentType {
    COMMENT_CREATED,
    COMMENT_REMOVED,
    REACTION_CREATED,
    REACTION_REMOVED;

    /** The snake_case wire value (e.g. {@code "comment_created"}). */
    public String wireValue() {
        return name().toLowerCase();
    }
}
