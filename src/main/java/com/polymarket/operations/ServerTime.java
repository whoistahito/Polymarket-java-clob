package com.polymarket.operations;

import java.time.Instant;
import lombok.NonNull;

/** The exchange's own clock reading, used to align signed timestamps with the server. */
public record ServerTime(@NonNull Instant at) {


    public static ServerTime ofEpochSeconds(long epochSeconds) {
        return new ServerTime(Instant.ofEpochSecond(epochSeconds));
    }
}
