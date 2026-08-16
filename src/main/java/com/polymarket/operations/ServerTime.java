package com.polymarket.operations;

import java.time.Instant;
import java.util.Objects;

/** The exchange's own clock reading, used to align signed timestamps with the server. */
public record ServerTime(Instant at) {

    public ServerTime {
        Objects.requireNonNull(at, "at");
    }

    public static ServerTime ofEpochSeconds(long epochSeconds) {
        return new ServerTime(Instant.ofEpochSecond(epochSeconds));
    }
}
