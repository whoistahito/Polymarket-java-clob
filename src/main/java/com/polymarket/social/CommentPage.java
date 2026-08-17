package com.polymarket.social;

import java.util.Optional;

/** Immutable sort/page filter for a user's comments. {@code limit} is required so no read is unbounded. */
public final class CommentPage {

    private final int limit;
    private final Integer offset;
    private final String order;
    private final Boolean ascending;

    private CommentPage(int limit, Integer offset, String order, Boolean ascending) {
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1: " + limit);
        this.limit = limit;
        this.offset = offset;
        this.order = order;
        this.ascending = ascending;
    }

    public static CommentPage limit(int limit) {
        return new CommentPage(limit, null, null, null);
    }

    public CommentPage offset(int offset) {
        return new CommentPage(limit, offset, order, ascending);
    }

    public CommentPage order(String order) {
        return new CommentPage(limit, offset, order, ascending);
    }

    public CommentPage ascending(boolean ascending) {
        return new CommentPage(limit, offset, order, ascending);
    }

    public int limit() {
        return limit;
    }

    public Optional<Integer> offset() {
        return Optional.ofNullable(offset);
    }

    public Optional<String> order() {
        return Optional.ofNullable(order);
    }

    public Optional<Boolean> ascending() {
        return Optional.ofNullable(ascending);
    }
}
