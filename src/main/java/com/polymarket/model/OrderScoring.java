package com.polymarket.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

/** Response indicating whether a single order is scoring. */
@Value
@Builder
@JsonDeserialize(builder = OrderScoring.OrderScoringBuilder.class)
public class OrderScoring {
    boolean scoring;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class OrderScoringBuilder {}
}
