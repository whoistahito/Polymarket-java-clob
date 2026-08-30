package com.polymarket.rewards;

import java.util.List;
import lombok.NonNull;
import java.util.Optional;

/**
 * One page of a reward read. It carries its own next cursor, so a caller decides whether to
 * fetch again; the SDK never hides an unbounded walk behind a single call.
 */
public record RewardPage<T>(@NonNull List<T> items, @NonNull Optional<RewardCursor> nextCursor,
        int limit, int count, @NonNull Optional<Integer> totalCount) {

    public RewardPage {
        items = List.copyOf(items);
    }
}
