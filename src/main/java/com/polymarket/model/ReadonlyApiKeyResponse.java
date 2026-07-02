package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from creating a readonly API key. Mirrors TS {@code ReadonlyApiKeyResponse}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadonlyApiKeyResponse {

    @JsonProperty("apiKey")
    private String apiKey;
}
