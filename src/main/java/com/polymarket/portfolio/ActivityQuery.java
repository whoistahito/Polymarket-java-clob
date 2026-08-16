package com.polymarket.portfolio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    public static ActivityQuery forUser(String user) {
        Objects.requireNonNull(user, "user");
        if (user.isBlank()) throw new IllegalArgumentException("user must not be blank");
        return new ActivityQuery(user, List.of(), null, null, null, null);
    }

    public ActivityQuery kinds(List<ActivityKind.Known> kinds) {
        return new ActivityQuery(user, List.copyOf(kinds), includeDepositsAndWithdrawals, side,
                from, to);
    }

    /** The API excludes them by default even when {@code kinds} asks for them. */
    public ActivityQuery includeDepositsAndWithdrawals(boolean include) {
        return new ActivityQuery(user, kinds, include, side, from, to);
    }

    public ActivityQuery side(Side side) {
        return new ActivityQuery(user, kinds, includeDepositsAndWithdrawals,
                Objects.requireNonNull(side, "side"), from, to);
    }

    public ActivityQuery from(Instant from) {
        return new ActivityQuery(user, kinds, includeDepositsAndWithdrawals, side,
                Objects.requireNonNull(from, "from"), to);
    }

    public ActivityQuery to(Instant to) {
        return new ActivityQuery(user, kinds, includeDepositsAndWithdrawals, side, from,
                Objects.requireNonNull(to, "to"));
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
