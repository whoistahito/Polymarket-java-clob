package com.polymarket.trading;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Settlement disposition for one accepted submission's trade IDs. */
public sealed interface ReconciliationOutcome {

    /** Every requested trade confirmed with its transaction hash and none failed. */
    record Confirmed(@NonNull List<SettledTrade> trades) implements ReconciliationOutcome {
        public Confirmed {
            trades = List.copyOf(trades);
        }
    }

    /** At least one requested trade reached FAILED. */
    record Failed(@NonNull List<SettledTrade> trades) implements ReconciliationOutcome {
        public Failed {
            trades = List.copyOf(trades);
        }
    }

    /**
     * The server's own records contradict themselves or each other. Distinct from
     * {@link Failed}: nothing here says the trade failed, only that it cannot be believed.
     */
    record Inconsistent(@NonNull List<SettledTrade> trades, @NonNull List<String> contradictions)
            implements ReconciliationOutcome {
        public Inconsistent {
            trades = List.copyOf(trades);
            contradictions = List.copyOf(contradictions);
        }
    }

    /**
     * The local deadline passed before every trade settled. Not a failure — the order, and the
     * RFQ ID where a Combo request produced one, may still settle later. {@code observed} is what
     * the last read actually saw, so missing, MATCHED, MINED, RETRYING and an unrecognised status
     * stay distinguishable instead of collapsing into an unexplained wait.
     */
    record Pending(@NonNull String orderId, @NonNull List<String> tradeIds,
            @NonNull Optional<String> rfqId, @NonNull List<SettledTrade> observed)
            implements ReconciliationOutcome {
        public Pending {
            tradeIds = List.copyOf(tradeIds);
            observed = List.copyOf(observed);
        }
    }
}
