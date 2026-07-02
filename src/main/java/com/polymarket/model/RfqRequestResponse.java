package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from creating an RFQ request. Mirrors TS {@code RfqRequestResponse}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqRequestResponse {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("error")
    private String error;
}
