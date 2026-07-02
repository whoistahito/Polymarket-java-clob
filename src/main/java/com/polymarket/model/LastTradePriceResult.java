package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = LastTradePriceResult.LastTradePriceResultBuilder.class)
public class LastTradePriceResult {
    @JsonProperty("token_id")
    String tokenId;
    BigDecimal price;
    Side side;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class LastTradePriceResultBuilder {}
}
