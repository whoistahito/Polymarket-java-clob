package com.polymarket.social;

import java.util.Objects;
import java.util.Optional;

/** One account backing a public profile, as listed by Gamma's {@code users} array. */
public record LinkedAccount(String id, Optional<Boolean> creator, Optional<Boolean> moderator) {

    public LinkedAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(moderator, "moderator");
    }
}
