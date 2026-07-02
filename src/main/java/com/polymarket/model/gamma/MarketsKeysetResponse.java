package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Response from {@code GET /markets/keyset}. {@code nextCursor} is the opaque token to pass as
 * {@code after_cursor} on the next request; it is omitted (null) on the last page.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketsKeysetResponse {
    private List<GammaMarketDetail> markets;

    @JsonProperty("next_cursor")
    private String nextCursor;
}
