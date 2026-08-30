package com.polymarket.portfolio;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * One page of a portfolio read. Continuation is the caller's decision: nothing here walks
 * the remaining pages for you.
 */
public final class PortfolioPage<T> {

    private final List<T> items;
    private final PageCursor nextCursor;
    private final boolean complete;

    private PortfolioPage(@NonNull List<T> items, PageCursor nextCursor, boolean complete) {
        this.items = List.copyOf(items);
        this.nextCursor = nextCursor;
        this.complete = complete;
    }

    /** The source had no further rows. */
    public static <T> PortfolioPage<T> lastPage(List<T> items) {
        return new PortfolioPage<>(items, null, true);
    }

    public static <T> PortfolioPage<T> withNext(List<T> items, @NonNull PageCursor next) {
        return new PortfolioPage<>(items, next, false);
    }

    /** A full page that cannot be continued: the endpoint's documented offset budget is spent. */
    public static <T> PortfolioPage<T> atPaginationLimit(List<T> items) {
        return new PortfolioPage<>(items, null, false);
    }

    public List<T> items() {
        return items;
    }

    public Optional<PageCursor> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /** False with no cursor means the offset budget ran out — narrow the query to read further. */
    public boolean complete() {
        return complete;
    }
}
