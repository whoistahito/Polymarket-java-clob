package com.polymarket.model.data;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * A trade returned by {@code GET https://data-api.polymarket.com/trades}.
 *
 * <p>Represents an executed order where outcome tokens were bought or sold.
 * Mirrors the Rust SDK's {@code data::types::response::Trade} struct.
 * Optional string fields that the API may return as empty strings are mapped
 * to {@code null} via {@link JsonSetter} with {@link Nulls#AS_EMPTY}.
 */
@Value
@Builder
@JsonDeserialize(builder = DataTrade.DataTradeBuilder.class)
public class DataTrade {

    /** The trader's proxy wallet address (0x-prefixed). */
    String proxyWallet;

    /** Trade side: BUY or SELL. */
    DataSide side;

    /** The outcome token asset identifier (large integer as string). */
    String asset;

    /** The market condition ID (0x-prefixed 64-hex string). */
    String conditionId;

    /** Number of tokens traded. */
    BigDecimal size;

    /** Execution price per token. */
    BigDecimal price;

    /** Unix timestamp when the trade occurred. */
    long timestamp;

    /** Market title / question. */
    String title;

    /** Market URL slug. */
    String slug;

    /** Market icon URL. */
    String icon;

    /** Parent event URL slug. */
    String eventSlug;

    /** Outcome name (e.g. "Yes", "No"). */
    String outcome;

    /** Outcome index within the market (0 or 1 for binary markets). */
    int outcomeIndex;

    /** Trader's display name; {@code null} when the API returns {@code ""} or omits the field. */
    String name;

    /** Trader's pseudonym; {@code null} when the API returns {@code ""} or omits the field. */
    String pseudonym;

    /** Trader's bio; {@code null} when the API returns {@code ""} or omits the field. */
    String bio;

    /** Trader's profile image URL; {@code null} when the API returns {@code ""} or omits the field. */
    String profileImage;

    /** Trader's optimized profile image URL; {@code null} when empty or absent. */
    String profileImageOptimized;

    /** On-chain transaction hash. */
    String transactionHash;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class DataTradeBuilder {

        /** Map empty string → {@code null} for optional display fields. */
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public DataTradeBuilder name(String v) {
            this.name = (v != null && v.isEmpty()) ? null : v;
            return this;
        }

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public DataTradeBuilder pseudonym(String v) {
            this.pseudonym = (v != null && v.isEmpty()) ? null : v;
            return this;
        }

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public DataTradeBuilder bio(String v) {
            this.bio = (v != null && v.isEmpty()) ? null : v;
            return this;
        }

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public DataTradeBuilder profileImage(String v) {
            this.profileImage = (v != null && v.isEmpty()) ? null : v;
            return this;
        }

        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public DataTradeBuilder profileImageOptimized(String v) {
            this.profileImageOptimized = (v != null && v.isEmpty()) ? null : v;
            return this;
        }
    }
}
