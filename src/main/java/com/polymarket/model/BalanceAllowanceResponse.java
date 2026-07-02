package com.polymarket.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = BalanceAllowanceResponse.BalanceAllowanceResponseBuilder.class)
public class BalanceAllowanceResponse {
    String balance;
    String allowance;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class BalanceAllowanceResponseBuilder {}
}
