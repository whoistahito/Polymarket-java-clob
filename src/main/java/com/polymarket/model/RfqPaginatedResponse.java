package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paginated response for RFQ list endpoints.
 * Mirrors TS {@code RfqPaginatedResponse<T>}.
 *
 * @param <T> The element type (e.g., {@link RfqRequest} or {@link RfqQuote}).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqPaginatedResponse<T> {

    @JsonProperty("data")
    private List<T> data;

    @JsonProperty("next_cursor")
    private String nextCursor;

    @JsonProperty("limit")
    private int limit;

    @JsonProperty("count")
    private int count;

    @JsonProperty("total_count")
    private Integer totalCount;
}
