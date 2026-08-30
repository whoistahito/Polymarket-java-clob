package com.polymarket.portfolio;

/**
 * Where the next portfolio page starts. The Data API pages by offset, so the cursor is an
 * offset and the page size that produced it.
 */
public record PageCursor(int offset, int limit) {

    public PageCursor {
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative: " + offset);
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1: " + limit);
    }

    public static PageCursor firstPage(int limit) {
        return new PageCursor(0, limit);
    }

    public PageCursor next() {
        return new PageCursor(offset + limit, limit);
    }
}
