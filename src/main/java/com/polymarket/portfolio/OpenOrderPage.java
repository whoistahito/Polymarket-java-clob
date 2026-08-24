package com.polymarket.portfolio;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * One page of resting orders. It carries its own next cursor, so continuing the walk is the
 * caller's decision; nothing here reads the remaining pages for you.
 */
public record OpenOrderPage(@NonNull List<OpenOrder> items,
        @NonNull Optional<OrderCursor> nextCursor, int limit, int count) {

    public OpenOrderPage {
        items = List.copyOf(items);
    }
}
