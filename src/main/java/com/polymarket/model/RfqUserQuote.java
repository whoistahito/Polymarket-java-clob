package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/** Input for creating an RFQ quote. Mirrors TS {@code RfqUserQuote}. */
@Value
@Builder
public class RfqUserQuote {

    String requestId;
    String tokenID;
    double price;
    double size;
    Side side;
}
