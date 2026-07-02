package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = BanStatus.BanStatusBuilder.class)
public class BanStatus {
    @JsonProperty("closed_only")
    Boolean closedOnly;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class BanStatusBuilder {}
}
