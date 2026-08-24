package com.polymarket.markets;

import java.util.Optional;
import lombok.NonNull;

/** A discovery tag. */
public record MarketTag(@NonNull String id, @NonNull Optional<String> label, Optional<String> slug) {

}
