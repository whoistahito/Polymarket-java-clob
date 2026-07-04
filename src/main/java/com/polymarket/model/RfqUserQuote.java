package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Input for creating an RFQ quote. Mirrors TS {@code RfqUserQuote}. */
@Value
@Builder
public class RfqUserQuote {

    String requestId;
    String tokenID;
    BigDecimal price;
    BigDecimal size;
    Side side;
}
