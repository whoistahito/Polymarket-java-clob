package com.polymarket.trading;

import java.util.Locale;
import java.util.Optional;
import lombok.NonNull;

/** A trade's status as the server sent it; the raw value survives a status this release doesn't know. */
public record TradeStatus(@NonNull String raw) {

    /** clob-openapi.yaml Trade.status, pinned in protocol/trades.json. */
    public enum Known {
        MATCHED, MINED, CONFIRMED, RETRYING, FAILED
    }

    private static final String WIRE_PREFIX = "TRADE_STATUS_";

    public Optional<Known> known() {
        String bare = raw.toUpperCase(Locale.ROOT);
        if (bare.startsWith(WIRE_PREFIX)) bare = bare.substring(WIRE_PREFIX.length());
        for (Known candidate : Known.values()) {
            if (candidate.name().equals(bare)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public boolean is(Known known) {
        return known().filter(known::equals).isPresent();
    }

    /** CONFIRMED or FAILED. MINED is NOT terminal: a mined trade can still return to RETRYING. */
    public boolean isTerminal() {
        return is(Known.CONFIRMED) || is(Known.FAILED);
    }
}
