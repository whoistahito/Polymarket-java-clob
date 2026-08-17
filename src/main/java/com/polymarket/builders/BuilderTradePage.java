package com.polymarket.builders;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of builder trades. It carries its own next cursor, so a caller decides whether to
 * fetch again; the SDK never hides an unbounded walk behind a single call.
 */
public record BuilderTradePage(
        List<BuilderTrade> items, Optional<BuilderCursor> nextCursor, int limit, int count) {

    public BuilderTradePage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(nextCursor, "nextCursor");
    }
}
