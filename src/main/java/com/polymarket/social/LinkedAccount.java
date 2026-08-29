package com.polymarket.social;

import java.util.Optional;
import lombok.NonNull;

/** One account backing a public profile, as listed by Gamma's {@code users} array. */
public record LinkedAccount(@NonNull String id, @NonNull Optional<Boolean> creator,
        @NonNull Optional<Boolean> moderator) {

}
