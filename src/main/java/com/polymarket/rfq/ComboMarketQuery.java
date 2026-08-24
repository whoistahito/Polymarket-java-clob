package com.polymarket.rfq;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Immutable Combo catalog filter. A page size is always required, so no read can run unbounded. */
public final class ComboMarketQuery {

    /** Observed accepted range on 2026-08-24; both ends are rejected with HTTP 400. */
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final int pageSize;
    private final String cursor;
    private final List<String> exclude;

    private ComboMarketQuery(int pageSize, String cursor, List<String> exclude) {
        this.pageSize = pageSize;
        this.cursor = cursor;
        this.exclude = exclude;
    }

    public static ComboMarketQuery pageSize(int pageSize) {
        if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("a Combo catalog page size must be "
                    + MIN_PAGE_SIZE + "-" + MAX_PAGE_SIZE + ", got " + pageSize);
        }
        return new ComboMarketQuery(pageSize, null, List.of());
    }

    /** Continues from a previous page's cursor, sent back verbatim as the gateway issued it. */
    public ComboMarketQuery cursor(@NonNull String cursor) {
        if (cursor.isBlank()) throw new IllegalArgumentException("cursor must not be blank");
        return new ComboMarketQuery(pageSize, cursor, exclude);
    }

    /** Omits markets by condition id — the ones a caller has already shown or selected. */
    public ComboMarketQuery exclude(@NonNull List<String> conditionIds) {
        return new ComboMarketQuery(pageSize, cursor, List.copyOf(conditionIds));
    }

    public int pageSizeValue() {
        return pageSize;
    }

    public Optional<String> cursorValue() {
        return Optional.ofNullable(cursor);
    }

    public List<String> excluded() {
        return exclude;
    }
}
