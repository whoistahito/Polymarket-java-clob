package com.polymarket.markets;

import java.util.Optional;

/** Immutable filter for event discovery. Unset fields are never sent. */
public final class EventQuery {

    private final Integer limit;
    private final Integer offset;
    private final String order;
    private final Boolean ascending;
    private final Boolean active;
    private final Boolean closed;
    private final String tagSlug;

    private EventQuery(Integer limit, Integer offset, String order, Boolean ascending,
            Boolean active, Boolean closed, String tagSlug) {
        this.limit = limit;
        this.offset = offset;
        this.order = order;
        this.ascending = ascending;
        this.active = active;
        this.closed = closed;
        this.tagSlug = tagSlug;
    }

    public static EventQuery create() {
        return new EventQuery(null, null, null, null, null, null, null);
    }

    public EventQuery limit(int limit) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public EventQuery offset(int offset) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public EventQuery order(String order) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public EventQuery ascending(boolean ascending) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public EventQuery active(boolean active) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public EventQuery closed(boolean closed) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public EventQuery tagSlug(String tagSlug) {
        return new EventQuery(limit, offset, order, ascending, active, closed, tagSlug);
    }

    public Optional<Integer> limit() {
        return Optional.ofNullable(limit);
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

    public Optional<Boolean> active() {
        return Optional.ofNullable(active);
    }

    public Optional<Boolean> closed() {
        return Optional.ofNullable(closed);
    }

    public Optional<String> tagSlug() {
        return Optional.ofNullable(tagSlug);
    }
}
