package com.polymarket.rfq;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** An RFQ's status as the Builder Gateway sent it; the raw value survives a status this release doesn't know. */
public record RfqStatus(String raw) {

    public enum Known {
        AWAITING_REQUESTER_ACCEPTANCE, AWAITING_MAKER_CONFIRMATION, EXECUTING, MINED, RETRYING,
        CONFIRMED, FILLED, FAILED, EXPIRED, CANCELED
    }

    private static final Set<Known> NON_TERMINAL = Set.of(Known.AWAITING_REQUESTER_ACCEPTANCE,
            Known.AWAITING_MAKER_CONFIRMATION, Known.EXECUTING, Known.MINED, Known.RETRYING);
    private static final Set<Known> TERMINAL_WITHOUT_FILL =
            Set.of(Known.FAILED, Known.EXPIRED, Known.CANCELED);

    public RfqStatus {
        Objects.requireNonNull(raw, "raw");
    }

    public Optional<Known> known() {
        for (Known candidate : Known.values()) {
            if (candidate.name().equalsIgnoreCase(raw)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public boolean is(Known known) {
        return known().filter(known::equals).isPresent();
    }

    public boolean isNonTerminal() {
        return known().filter(NON_TERMINAL::contains).isPresent();
    }

    public boolean isTerminalWithoutFill() {
        return known().filter(TERMINAL_WITHOUT_FILL::contains).isPresent();
    }
}
