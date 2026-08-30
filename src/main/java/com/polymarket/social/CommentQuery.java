package com.polymarket.social;

import java.util.Optional;
import lombok.NonNull;

/** Immutable filter for the general comment listing. {@code limit} is required so no read is unbounded. */
public final class CommentQuery {

    private final int limit;
    private final Integer offset;
    private final String order;
    private final Boolean ascending;
    private final ParentEntityType parentEntityType;
    private final String parentEntityId;
    private final Boolean includePositions;
    private final Boolean holdersOnly;

    private CommentQuery(int limit, Integer offset, String order, Boolean ascending,
            ParentEntityType parentEntityType, String parentEntityId, Boolean includePositions,
            Boolean holdersOnly) {
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1: " + limit);
        this.limit = limit;
        this.offset = offset;
        this.order = order;
        this.ascending = ascending;
        this.parentEntityType = parentEntityType;
        this.parentEntityId = parentEntityId;
        this.includePositions = includePositions;
        this.holdersOnly = holdersOnly;
    }

    public static CommentQuery limit(int limit) {
        return new CommentQuery(limit, null, null, null, null, null, null, null);
    }

    public CommentQuery offset(int offset) {
        return new CommentQuery(limit, offset, order, ascending, parentEntityType, parentEntityId,
                includePositions, holdersOnly);
    }

    public CommentQuery order(String order) {
        return new CommentQuery(limit, offset, order, ascending, parentEntityType, parentEntityId,
                includePositions, holdersOnly);
    }

    public CommentQuery ascending(boolean ascending) {
        return new CommentQuery(limit, offset, order, ascending, parentEntityType, parentEntityId,
                includePositions, holdersOnly);
    }

    /** Both fields travel together: Gamma has no route filtered by id alone. */
    public CommentQuery forEntity(@NonNull ParentEntityType type, @NonNull String id) {
        return new CommentQuery(limit, offset, order, ascending, type, id, includePositions,
                holdersOnly);
    }

    public CommentQuery includePositions(boolean includePositions) {
        return new CommentQuery(limit, offset, order, ascending, parentEntityType, parentEntityId,
                includePositions, holdersOnly);
    }

    public CommentQuery holdersOnly(boolean holdersOnly) {
        return new CommentQuery(limit, offset, order, ascending, parentEntityType, parentEntityId,
                includePositions, holdersOnly);
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

    public Optional<ParentEntityType> parentEntityType() {
        return Optional.ofNullable(parentEntityType);
    }

    public Optional<String> parentEntityId() {
        return Optional.ofNullable(parentEntityId);
    }

    public Optional<Boolean> includePositions() {
        return Optional.ofNullable(includePositions);
    }

    public Optional<Boolean> holdersOnly() {
        return Optional.ofNullable(holdersOnly);
    }
}
