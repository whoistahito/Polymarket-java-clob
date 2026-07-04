package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Input for creating an RFQ request from user-friendly parameters. Mirrors TS {@code RfqUserOrder}. */
@Value
@Builder
public class RfqUserOrder {

    String tokenID;
    BigDecimal price;
    BigDecimal size;
    Side side;
}
