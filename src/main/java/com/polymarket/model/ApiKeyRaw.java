package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

/**
 * Raw API key response as returned by the server before field-name remapping.
 *
 * <p>Mirrors the TypeScript {@code ApiKeyRaw} interface in
 * {@code clob-client/src/types.ts}. The server returns {@code apiKey} (camelCase),
 * which must be mapped to the {@code key} field on {@link ApiKeyCreds}.
 *
 * <p>Use {@link #toApiKeyCreds()} to obtain the normalised credential holder.
 */
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ApiKeyRaw.ApiKeyRawBuilder.class)
public class ApiKeyRaw {

    @JsonProperty("apiKey")
    String apiKey;

    String secret;

    String passphrase;

    /** Converts this raw response into the normalised {@link ApiKeyCreds} holder. */
    public com.polymarket.client.ApiKeyCreds toApiKeyCreds() {
        return new com.polymarket.client.ApiKeyCreds(apiKey, secret, passphrase);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class ApiKeyRawBuilder {}
}
