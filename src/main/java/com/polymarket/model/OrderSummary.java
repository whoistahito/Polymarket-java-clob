package com.polymarket.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = OrderSummary.OrderSummaryBuilder.class)
public class OrderSummary {
    String price;
    String size;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class OrderSummaryBuilder {}
}
