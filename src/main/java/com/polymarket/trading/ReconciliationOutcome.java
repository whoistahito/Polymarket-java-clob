package com.polymarket.trading;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Settlement disposition for one accepted submission's trade IDs. */
public sealed interface ReconciliationOutcome {

    /** Every requested trade reached a terminal state with none failed. */
    record Confirmed(List<SettledTrade> trades) implements ReconciliationOutcome {
        public Confirmed {
            trades = List.copyOf(trades);
        }
    }

    /** At least one requested trade reached FAILED. */
    record Failed(List<SettledTrade> trades) implements ReconciliationOutcome {
        public Failed {
            trades = List.copyOf(trades);
        }
    }

    /**
     * The local deadline passed before every trade reached a terminal state. Not a failure —
     * the order, and RFQ ID where a Combo request produced one, may still settle later.
     */
    record Pending(String orderId, List<String> tradeIds, Optional<String> rfqId)
            implements ReconciliationOutcome {
        public Pending {
            Objects.requireNonNull(orderId, "orderId");
            tradeIds = List.copyOf(tradeIds);
            Objects.requireNonNull(rfqId, "rfqId");
        }
    }
}
