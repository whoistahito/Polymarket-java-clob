package com.polymarket.rfq;

import com.polymarket.markets.PositionId;
import com.polymarket.trading.Side;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * A typed Combo RFQ business outcome. A local wait timeout is {@link Pending}, never a
 * reported failure — {@code rfqId} is the stable recovery handle for a later status read.
 */
public sealed interface RfqOutcome {

    String rfqId();

    /**
     * A quote ready to accept before {@code expiresAt} (AWAITING_REQUESTER_ACCEPTANCE).
     * {@code direction} is the direction the gateway echoed for the request, so acceptance
     * cannot restate it; {@code comboPositionId} is {@code request.yes_position_id}.
     */
    record Quoted(@NonNull String rfqId, @NonNull String quoteId, @NonNull Side direction,
            @NonNull PositionId comboPositionId, @NonNull List<PositionId> legs,
            @NonNull QuoteAmounts amounts, @NonNull Instant expiresAt, @NonNull String builderCode)
            implements RfqOutcome {
        public Quoted {
            legs = List.copyOf(legs);
        }
    }

    /** Routed execution succeeded (status CONFIRMED or FILLED — the fixture groups both as success). */
    record Confirmed(@NonNull String rfqId, @NonNull String status,
            @NonNull Optional<String> takerOrderHash) implements RfqOutcome {
    }

    /**
     * Still non-terminal (e.g. awaiting a maker); not a local timeout, just not resolved yet.
     * A retried acceptance may omit {@code takerOrderHash}, so its absence proves nothing.
     */
    record Waiting(@NonNull String rfqId, @NonNull RfqStatus status,
            @NonNull Optional<String> takerOrderHash) implements RfqOutcome {
    }

    /**
     * A definitive business failure: no maker quoted, the maker declined, or execution failed.
     * The wire protocol reports all three as status FAILED with only a free-text nested error,
     * so they are not split into separate types without inventing an unverified error schema.
     */
    record Failed(@NonNull String rfqId, @NonNull String reason) implements RfqOutcome {
    }

    record Expired(@NonNull String rfqId) implements RfqOutcome {
    }

    record Canceled(@NonNull String rfqId) implements RfqOutcome {
    }

    /** The local wait deadline passed while still non-terminal; not a failed trade. */
    record Pending(@NonNull String rfqId) implements RfqOutcome {
    }

    /**
     * The gateway refused the exchange itself (HTTP 4xx/5xx), which is a transport-level
     * verdict, not the RFQ's business result. Kept apart from {@link Failed} deliberately.
     */
    record Rejected(@NonNull String rfqId, int httpStatus, @NonNull String reason)
            implements RfqOutcome {
    }

    /**
     * A status read arrived before the RFQ was accepted (HTTP 409), so the state machine has
     * nothing to report yet. The Quote from the create response is still the thing to accept.
     */
    record NotYetAccepted(@NonNull String rfqId) implements RfqOutcome {
    }

    /** A status this release does not know; kept as raw text rather than guessed at. */
    record Unknown(@NonNull String rfqId, @NonNull String rawStatus) implements RfqOutcome {
    }
}
