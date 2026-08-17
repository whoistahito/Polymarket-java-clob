package com.polymarket.rfq;

import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A Combo quote request. BUY sizes in pUSD notional, SELL sizes in Combo shares — the two exchange units. */
public sealed interface RfqRequest {

    List<PositionId> legs();

    record Buy(List<PositionId> legs, PusdAmount notional) implements RfqRequest {
        public Buy {
            legs = requireLegs(legs);
            Objects.requireNonNull(notional, "notional");
        }
    }

    record Sell(List<PositionId> legs, ShareQuantity shares) implements RfqRequest {
        public Sell {
            legs = requireLegs(legs);
            Objects.requireNonNull(shares, "shares");
        }
    }

    /** Official: 2-50 unique legs per request. */
    private static List<PositionId> requireLegs(List<PositionId> legs) {
        Objects.requireNonNull(legs, "legs");
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
