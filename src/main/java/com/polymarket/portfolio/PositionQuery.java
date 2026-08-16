package com.polymarket.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable position filter. Unset fields are never sent, so the server's defaults apply. */
public final class PositionQuery {

    private final String user;
    private final List<String> conditionIds;
    private final BigDecimal sizeThreshold;
    private final Boolean redeemable;
    private final Boolean mergeable;
    private final Boolean includeArchived;

    private PositionQuery(String user, List<String> conditionIds, BigDecimal sizeThreshold,
            Boolean redeemable, Boolean mergeable, Boolean includeArchived) {
        this.user = user;
        this.conditionIds = conditionIds;
        this.sizeThreshold = sizeThreshold;
        this.redeemable = redeemable;
        this.mergeable = mergeable;
        this.includeArchived = includeArchived;
    }

    /** The Data API rejects a position read without a user, so it is required here. */
    public static PositionQuery forUser(String user) {
        Objects.requireNonNull(user, "user");
        if (user.isBlank()) throw new IllegalArgumentException("user must not be blank");
        return new PositionQuery(user, List.of(), null, null, null, null);
    }

    public PositionQuery markets(List<String> conditionIds) {
        return new PositionQuery(user, List.copyOf(conditionIds), sizeThreshold, redeemable,
                mergeable, includeArchived);
    }

    public PositionQuery sizeThreshold(BigDecimal sizeThreshold) {
        return new PositionQuery(user, conditionIds,
                Objects.requireNonNull(sizeThreshold, "sizeThreshold"), redeemable, mergeable,
                includeArchived);
    }

    public PositionQuery redeemable(boolean redeemable) {
        return new PositionQuery(user, conditionIds, sizeThreshold, redeemable, mergeable,
                includeArchived);
    }

    public PositionQuery mergeable(boolean mergeable) {
        return new PositionQuery(user, conditionIds, sizeThreshold, redeemable, mergeable,
                includeArchived);
    }

    /** Archived-but-active markets are excluded by default (changelog 2026-08-10). */
    public PositionQuery includeArchived(boolean includeArchived) {
        return new PositionQuery(user, conditionIds, sizeThreshold, redeemable, mergeable,
                includeArchived);
    }

    public String user() {
        return user;
    }

    public List<String> conditionIds() {
        return conditionIds;
    }

    public Optional<BigDecimal> sizeThreshold() {
        return Optional.ofNullable(sizeThreshold);
    }

    public Optional<Boolean> redeemable() {
        return Optional.ofNullable(redeemable);
    }

    public Optional<Boolean> mergeable() {
        return Optional.ofNullable(mergeable);
    }

    public Optional<Boolean> includeArchived() {
        return Optional.ofNullable(includeArchived);
    }
}
