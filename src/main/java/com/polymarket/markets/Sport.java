package com.polymarket.markets;

import java.util.Objects;
import java.util.Optional;

/** Sports metadata; {@code id} is Gamma's sport identifier or abbreviation. */
public record Sport(
        String id,
        Optional<String> image,
        Optional<String> resolutionSource,
        Optional<String> ordering) {

    public Sport {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(resolutionSource, "resolutionSource");
        Objects.requireNonNull(ordering, "ordering");
    }
}
