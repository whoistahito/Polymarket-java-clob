package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Builder API key credentials. Mirrors TS {@code BuilderApiKey}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuilderApiKey {

    @JsonProperty("key")
    private String key;

    @JsonProperty("secret")
    private String secret;

    @JsonProperty("passphrase")
    private String passphrase;
}
