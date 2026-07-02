package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * A single outcome token within a Polymarket market.
 *
 * <p>Mirrors the TypeScript {@code Token} interface in {@code clob-client/src/types.ts}
 * and the Rust {@code Token} struct in {@code rs-clob-client/src/clob/types/response.rs}.
 * Rust adds the {@code winner} field (defaulting to {@code false}).
 */
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = Token.TokenBuilder.class)
public class Token {

    /** The ERC-1155 token ID for this outcome. */
    @JsonProperty("token_id")
    String tokenId;

    /** Human-readable outcome label, e.g. {@code "Yes"} or {@code "No"}. */
    String outcome;

    /** Last known mid-point price for this outcome token. */
    BigDecimal price;

    /**
     * Whether this outcome was the winning resolution.
     * Defaults to {@code false} for unresolved markets.
     */
    @Builder.Default
    boolean winner = false;

    @JsonPOJOBuilder(withPrefix = "")
    public static class TokenBuilder {}
}
