package com.polymarket.rewards;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of a reward read. It carries its own next cursor, so a caller decides whether to
 * fetch again; the SDK never hides an unbounded walk behind a single call.
 */
public record RewardPage<T>(List<T> items, Optional<RewardCursor> nextCursor, int limit, int count,
        Optional<Integer> totalCount) {

    public RewardPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(nextCursor, "nextCursor");
        Objects.requireNonNull(totalCount, "totalCount");
    }
}
