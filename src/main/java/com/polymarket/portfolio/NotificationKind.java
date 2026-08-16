package com.polymarket.portfolio;

import java.util.Objects;
import java.util.Optional;

/**
 * What a notification announces. The raw numeric code is kept, so a type added after this
 * release still reads instead of failing the whole list.
 */
public record NotificationKind(int code) {

    /** The codes the CLOB API documented on 2026-08-16. */
    public enum Known {
        ORDER_CANCELLATION(1),
        ORDER_FILL(2),
        MARKET_REGISTERED(3),
        MARKET_RESOLVED(4),
        REWARD_PAYOUT(5),
        CHILD_COMMENT_CREATED(6);

        private final int code;

        Known(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public static NotificationKind of(Known known) {
        return new NotificationKind(Objects.requireNonNull(known, "known").code());
    }

    public Optional<Known> known() {
        for (Known candidate : Known.values()) {
            if (candidate.code == code) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public boolean isKnown() {
        return known().isPresent();
    }

    public boolean is(Known known) {
        return known().filter(known::equals).isPresent();
    }
}
