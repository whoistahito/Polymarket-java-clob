package com.polymarket.social;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** The profile matches from a public search; events and tags stay Markets' own search. */
public record SocialSearchResults(@NonNull List<SearchProfile> profiles, Optional<Boolean> hasMore,
        Optional<Integer> totalResults) {

    public SocialSearchResults {
        profiles = List.copyOf(profiles);
    }
}
