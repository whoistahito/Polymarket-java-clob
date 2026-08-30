package com.polymarket.rfq;

import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.util.List;
import java.util.Set;
import lombok.NonNull;

/** A Combo quote request. BUY sizes in pUSD notional, SELL sizes in Combo shares — the two exchange units. */
public sealed interface RfqRequest {

    List<PositionId> legs();

    record Buy(@NonNull List<PositionId> legs, @NonNull PusdAmount notional) implements RfqRequest {
        public Buy {
            legs = requireLegs(legs);
        }
    }

    record Sell(@NonNull List<PositionId> legs, @NonNull ShareQuantity shares) implements RfqRequest {
        public Sell {
            legs = requireLegs(legs);
        }
    }

    /** Official: 2-50 unique legs per request. */
    private static List<PositionId> requireLegs(@NonNull List<PositionId> legs) {
        List<PositionId> copy = List.copyOf(legs);
        if (copy.size() < 2 || copy.size() > 50) {
            throw new IllegalArgumentException(
                    "an RFQ needs 2-50 legs, got " + copy.size());
        }
        if (Set.copyOf(copy).size() != copy.size()) {
            throw new IllegalArgumentException("RFQ legs must be unique");
        }
        return copy;
    }
}
