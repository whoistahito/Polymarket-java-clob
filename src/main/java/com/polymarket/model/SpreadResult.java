package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = SpreadResult.SpreadResultBuilder.class)
public class SpreadResult {
    @JsonProperty("token_id")
    String tokenId;
    BigDecimal spread;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class SpreadResultBuilder {}
}
