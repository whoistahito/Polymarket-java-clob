package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for a builder API key listing entry. Mirrors TS {@code BuilderApiKeyResponse}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuilderApiKeyResponse {

    @JsonProperty("key")
    private String key;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("revokedAt")
    private String revokedAt;
}
