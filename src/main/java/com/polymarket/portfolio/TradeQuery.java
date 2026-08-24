package com.polymarket.portfolio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Immutable trade filter. The offset budget is small, so {@code from}/{@code to} are how a
 * caller reads history deeper than one budget's worth.
 */
public final class TradeQuery {

    private final String user;
    private final List<String> conditionIds;
    private final Side side;
    private final Boolean takerOnly;
    private final Instant from;
    private final Instant to;

    private TradeQuery(String user, List<String> conditionIds, Side side, Boolean takerOnly,
            Instant from, Instant to) {
        this.user = user;
        this.conditionIds = conditionIds;
        this.side = side;
        this.takerOnly = takerOnly;
        this.from = from;
        this.to = to;
    }

    public static TradeQuery create() {
        return new TradeQuery(null, List.of(), null, null, null, null);
    }

    public TradeQuery user(@NonNull String user) {
        return new TradeQuery(QueryBoundaries.address(user, "user"), conditionIds, side, takerOnly,
                from, to);
    }

    public TradeQuery markets(@NonNull List<String> conditionIds) {
        return new TradeQuery(user, QueryBoundaries.conditionIds(conditionIds), side, takerOnly,
                from, to);
    }

    public TradeQuery side(@NonNull Side side) {
        return new TradeQuery(user, conditionIds, side, takerOnly, from, to);
    }

    public TradeQuery takerOnly(boolean takerOnly) {
        return new TradeQuery(user, conditionIds, side, takerOnly, from, to);
    }

    public TradeQuery from(@NonNull Instant from) {
        QueryBoundaries.windowBound(from, "from");
        QueryBoundaries.orderedWindow(from, to);
        return new TradeQuery(user, conditionIds, side, takerOnly, from, to);
    }

    public TradeQuery to(@NonNull Instant to) {
        QueryBoundaries.windowBound(to, "to");
        QueryBoundaries.orderedWindow(from, to);
        return new TradeQuery(user, conditionIds, side, takerOnly, from, to);
    }

    public Optional<String> user() {
        return Optional.ofNullable(user);
    }

    public List<String> conditionIds() {
        return conditionIds;
    }

    public Optional<Side> side() {
        return Optional.ofNullable(side);
    }

    public Optional<Boolean> takerOnly() {
        return Optional.ofNullable(takerOnly);
    }

    public Optional<Instant> from() {
        return Optional.ofNullable(from);
    }

    public Optional<Instant> to() {
        return Optional.ofNullable(to);
    }
}
