package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/** Filter parameters for {@code getPricesHistory()}. Mirrors TS {@code PriceHistoryFilterParams}. */
@Value
@Builder
public class PriceHistoryFilterParams {

    String market;
    Long startTs;
    Long endTs;
    Integer fidelity;
    PriceHistoryInterval interval;
}
