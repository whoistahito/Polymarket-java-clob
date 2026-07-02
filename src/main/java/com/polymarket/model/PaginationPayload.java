package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = PaginationPayload.PaginationPayloadBuilder.class)
public class PaginationPayload<T> {
    Integer limit;
    Integer count;
    @JsonProperty("next_cursor")
    String nextCursor;
    List<T> data;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class PaginationPayloadBuilder<T> {}
}
