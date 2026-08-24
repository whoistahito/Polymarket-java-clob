package com.polymarket.markets;

import java.util.Optional;
import lombok.NonNull;

/** Sports metadata; {@code id} is Gamma's sport identifier or abbreviation. */
public record Sport(
        @NonNull String id,
        @NonNull Optional<String> image,
        Optional<String> resolutionSource,
        Optional<String> ordering) {

}
