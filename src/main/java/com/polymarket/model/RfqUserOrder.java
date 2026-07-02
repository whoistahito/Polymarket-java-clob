package com.polymarket.model;

import lombok.Builder;
import lombok.Value;

/** Input for creating an RFQ request from user-friendly parameters. Mirrors TS {@code RfqUserOrder}. */
@Value
@Builder
public class RfqUserOrder {

    String tokenID;
    double price;
    double size;
    Side side;
}
