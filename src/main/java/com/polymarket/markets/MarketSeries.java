package com.polymarket.markets;

import java.util.Optional;
import lombok.NonNull;

/** A recurring family of events, such as a league season. */
public record MarketSeries(@NonNull String id, @NonNull Optional<String> ticker,
        @NonNull Optional<String> slug, @NonNull Optional<String> title,
        @NonNull Optional<String> recurrence) {

}
