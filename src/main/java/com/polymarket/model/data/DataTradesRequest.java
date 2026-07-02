package com.polymarket.model.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Request parameters for {@code GET https://data-api.polymarket.com/trades}.
 *
 * <p>All fields are optional. {@code filterType} and {@code filterAmount} must be
 * provided together (the API requires both or neither).
 *
 * <p>Mirrors the Rust SDK's {@code data::types::request::TradesRequest} struct.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * DataTradesRequest req = DataTradesRequest.builder()
 *     .user("0x1234567890abcdef1234567890abcdef12345678")
 *     .side(DataSide.BUY)
 *     .limit(50)
 *     .takerOnly(true)
 *     .build();
 * }</pre>
 */
@Value
@Builder
public class DataTradesRequest {

    /** Filter by user proxy-wallet address (0x-prefixed, 40 hex chars). */
    String user;

    /**
     * Filter by condition IDs (markets). Mutually exclusive with {@link #eventIds}.
     * Serialised as a comma-separated {@code market} query parameter.
     */
    @Singular("market")
    List<String> markets;

    /**
     * Filter by event IDs. Mutually exclusive with {@link #markets}.
     * Serialised as a comma-separated {@code eventId} query parameter.
     */
    @Singular("eventId")
    List<String> eventIds;

    /** Maximum number of trades to return (0–10 000, default: 100). */
    Integer limit;

    /** Pagination offset (0–10 000, default: 0). */
    Integer offset;

    /** If {@code true}, only return taker trades (default: {@code true}). */
    Boolean takerOnly;

    /**
     * Filter type for minimum trade size.
     * Must be provided together with {@link #filterAmount}.
     */
    FilterType filterType;

    /**
     * Minimum trade size filter amount (must be &ge; 0).
     * Must be provided together with {@link #filterType}.
     */
    BigDecimal filterAmount;

    /** Filter by trade side (BUY or SELL). */
    DataSide side;

    /**
     * Converts this request to a query-parameter map suitable for URL encoding.
     *
     * @throws IllegalStateException if only one of {@code filterType}/{@code filterAmount} is set
     */
    public Map<String, String> toQueryParams() {
        if ((filterType == null) != (filterAmount == null)) {
            throw new IllegalStateException(
                "filterType and filterAmount must both be set or both be null");
        }

        Map<String, String> params = new LinkedHashMap<>();
        if (user != null)        params.put("user",        user);
        if (markets != null && !markets.isEmpty())
                                 params.put("market",      String.join(",", markets));
        if (eventIds != null && !eventIds.isEmpty())
                                 params.put("eventId",     String.join(",", eventIds));
        if (limit != null)       params.put("limit",       String.valueOf(limit));
        if (offset != null)      params.put("offset",      String.valueOf(offset));
        if (takerOnly != null)   params.put("takerOnly",   String.valueOf(takerOnly));
        if (filterType != null)  params.put("filterType",  filterType.toJson());
        if (filterAmount != null) params.put("filterAmount", filterAmount.toPlainString());
        if (side != null)        params.put("side",        side.toJson());
        return params;
    }
}
