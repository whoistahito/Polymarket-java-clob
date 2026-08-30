package com.polymarket.portfolio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Immutable activity filter. Only a closed set of caller-chosen types can be requested. */
public final class ActivityQuery {

    private final String user;
    private final List<ActivityKind.Known> kinds;
    private final Boolean includeDepositsAndWithdrawals;
    private final Side side;
    private final Instant from;
    private final Instant to;

    private ActivityQuery(String user, List<ActivityKind.Known> kinds,
            Boolean includeDepositsAndWithdrawals, Side side, Instant from, Instant to) {
        this.user = user;
        this.kinds = kinds;
        this.includeDepositsAndWithdrawals = includeDepositsAndWithdrawals;
        this.side = side;
        this.from = from;
        this.to = to;
    }

    /** The Data API rejects an activity read without a user, so it is required here. */
    public static ActivityQuery forUser(@NonNull String user) {
        return new ActivityQuery(QueryBoundaries.address(user, "user"), List.of(), null, null,
                null, null);
    }

    public ActivityQuery kinds(@NonNull List<ActivityKind.Known> kinds) {
        return new ActivityQuery(user, List.copyOf(kinds), includeDepositsAndWithdrawals, side,
                from, to);
    }

    /** The API excludes them by default even when {@code kinds} asks for them. */
    public ActivityQuery includeDepositsAndWithdrawals(boolean include) {
        return new ActivityQuery(user, kinds, include, side, from, to);
    }

    public ActivityQuery side(@NonNull Side side) {
        return new ActivityQuery(user, kinds, includeDepositsAndWithdrawals, side, from, to);
    }

    public ActivityQuery from(@NonNull Instant from) {
        QueryBoundaries.windowBound(from, "from");
        QueryBoundaries.orderedWindow(from, to);
        return new ActivityQuery(user, kinds, includeDepositsAndWithdrawals, side, from, to);
    }

    public ActivityQuery to(@NonNull Instant to) {
        QueryBoundaries.windowBound(to, "to");
        QueryBoundaries.orderedWindow(from, to);
        return new ActivityQuery(user, kinds, includeDepositsAndWithdrawals, side, from, to);
    }

    public String user() {
        return user;
    }

    public List<ActivityKind.Known> kinds() {
        return kinds;
    }

    public Optional<Boolean> includeDepositsAndWithdrawals() {
        return Optional.ofNullable(includeDepositsAndWithdrawals);
    }

    public Optional<Side> side() {
        return Optional.ofNullable(side);
    }

    public Optional<Instant> from() {
        return Optional.ofNullable(from);
    }

    public Optional<Instant> to() {
        return Optional.ofNullable(to);
    }
}
