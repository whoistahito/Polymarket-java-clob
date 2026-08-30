package com.polymarket.markets;

import java.util.Optional;

/** Immutable filter for market discovery. Unset fields are never sent. */
public final class MarketQuery {

    private final Integer limit;
    private final Boolean closed;

    private MarketQuery(Integer limit, Boolean closed) {
        this.limit = limit;
        this.closed = closed;
    }

    public static MarketQuery create() {
        return new MarketQuery(null, null);
    }

    public MarketQuery limit(int limit) {
        return new MarketQuery(limit, closed);
    }

    public MarketQuery closed(boolean closed) {
        return new MarketQuery(limit, closed);
    }

    public Optional<Integer> limit() {
        return Optional.ofNullable(limit);
    }

    public Optional<Boolean> closed() {
        return Optional.ofNullable(closed);
    }
}
