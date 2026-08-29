package com.polymarket.social;

import java.util.Optional;
import lombok.NonNull;

/** Immutable filter for profile search. A blank query would match the whole directory, so reject it. */
public final class SearchQuery {

    private final String q;
    private final Integer limitPerType;
    private final Integer page;

    private SearchQuery(@NonNull String q, Integer limitPerType, Integer page) {
        if (q.isBlank()) throw new IllegalArgumentException("q must not be blank");
        this.q = q;
        this.limitPerType = limitPerType;
        this.page = page;
    }

    public static SearchQuery of(@NonNull String q) {
        return new SearchQuery(q, null, null);
    }

    public SearchQuery limitPerType(int limitPerType) {
        return new SearchQuery(q, limitPerType, page);
    }

    public SearchQuery page(int page) {
        return new SearchQuery(q, limitPerType, page);
    }

    public String q() {
        return q;
    }

    public Optional<Integer> limitPerType() {
        return Optional.ofNullable(limitPerType);
    }

    public Optional<Integer> page() {
        return Optional.ofNullable(page);
    }
}
