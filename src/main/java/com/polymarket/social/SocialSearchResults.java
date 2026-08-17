package com.polymarket.social;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The profile matches from a public search; events and tags stay Markets' own search. */
public record SocialSearchResults(List<SearchProfile> profiles, Optional<Boolean> hasMore,
        Optional<Integer> totalResults) {

    public SocialSearchResults {
        profiles = List.copyOf(profiles);
        Objects.requireNonNull(hasMore, "hasMore");
        Objects.requireNonNull(totalResults, "totalResults");
    }
}
