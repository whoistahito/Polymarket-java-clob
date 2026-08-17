package com.polymarket.rfq;

import com.polymarket.markets.PositionId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A typed Combo RFQ business outcome. A local wait timeout is {@link Pending}, never a
 * reported failure — {@code rfqId} is the stable recovery handle for a later status read.
 */
public sealed interface RfqOutcome {

    String rfqId();

    /** A quote is ready to accept before {@code expiresAt} (AWAITING_REQUESTER_ACCEPTANCE). */
    record Quoted(String rfqId, String quoteId, List<PositionId> legs, long makerAmountBaseUnits,
            long takerAmountBaseUnits, Instant expiresAt, String builderCode) implements RfqOutcome {
        public Quoted {
            Objects.requireNonNull(rfqId, "rfqId");
            Objects.requireNonNull(quoteId, "quoteId");
            legs = List.copyOf(legs);
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(builderCode, "builderCode");
        }
    }

    /** Still non-terminal (e.g. awaiting a maker); not a local timeout, just not resolved yet. */
    record Waiting(String rfqId, RfqStatus status) implements RfqOutcome {
        public Waiting {
            Objects.requireNonNull(rfqId, "rfqId");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * A definitive business failure: no maker quoted, the maker declined, or execution failed.
     * The wire protocol reports all three as status FAILED with only a free-text nested error,
     * so they are not split into separate types without inventing an unverified error schema.
     */
    record Failed(String rfqId, String reason) implements RfqOutcome {
        public Failed {
            Objects.requireNonNull(rfqId, "rfqId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Expired(String rfqId) implements RfqOutcome {
        public Expired {
            Objects.requireNonNull(rfqId, "rfqId");
        }
    }

    record Canceled(String rfqId) implements RfqOutcome {
        public Canceled {
            Objects.requireNonNull(rfqId, "rfqId");
        }
    }

    /** The local wait deadline passed while still non-terminal; not a failed trade. */
    record Pending(String rfqId) implements RfqOutcome {
        public Pending {
            Objects.requireNonNull(rfqId, "rfqId");
        }
    }

    /** A status this release does not know; kept as raw text rather than guessed at. */
    record Unknown(String rfqId, String rawStatus) implements RfqOutcome {
        public Unknown {
            Objects.requireNonNull(rfqId, "rfqId");
            Objects.requireNonNull(rawStatus, "rawStatus");
        }
    }
}
