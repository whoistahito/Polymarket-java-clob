package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from creating an RFQ quote. Mirrors TS {@code RfqQuoteResponse}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqQuoteResponse {

    @JsonProperty("quoteId")
    private String quoteId;

    @JsonProperty("error")
    private String error;
}
