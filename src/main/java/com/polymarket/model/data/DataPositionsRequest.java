package com.polymarket.model.data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Request parameters for {@code GET https://data-api.polymarket.com/positions} (Ticket 025).
 *
 * <p>{@code user} is required by the API. {@code market} and {@code eventId} are mutually exclusive,
 * as documented.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * DataPositionsRequest req = DataPositionsRequest.builder()
 *     .user("0x1234567890abcdef1234567890abcdef12345678")
 *     .market("0xcondition...")
 *     .sizeThreshold(BigDecimal.ZERO)   // include dust residues
 *     .build();
 * }</pre>
 *
 * <p>Note the API's {@code sizeThreshold} defaults to {@code 1}, which HIDES any holding smaller
 * than one share. Reconciling a partial exit means passing {@code 0} explicitly.
 */
@Value
@Builder(toBuilder = true)
public class DataPositionsRequest {

    /** The holder's proxy wallet address (0x-prefixed, 40 hex chars). Required. */
    String user;

    /**
     * Filter by condition IDs. Mutually exclusive with {@link #eventIds}.
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

    /** Minimum size to include (API default: {@code 1} — pass {@code 0} to see dust). */
    BigDecimal sizeThreshold;

    /** Only return redeemable positions. */
    Boolean redeemable;

    /** Only return mergeable positions. */
    Boolean mergeable;

    /** Maximum positions to return (0–500, API default: 100). */
    Integer limit;

    /** Pagination offset (0–10 000, API default: 0). */
    Integer offset;

    /**
     * Sort key: {@code CURRENT}, {@code INITIAL}, {@code TOKENS}, {@code CASHPNL},
     * {@code PERCENTPNL}, {@code TITLE}, {@code RESOLVING}, {@code PRICE}, or {@code AVGPRICE}.
     */
    String sortBy;

    /** Sort direction: {@code ASC} or {@code DESC} (API default: {@code DESC}). */
    String sortDirection;

    /** Filter by market title (max 100 characters). */
    String title;

    /**
     * Converts this request to a query-parameter map suitable for URL encoding.
     *
     * @throws IllegalStateException if {@code user} is missing, or if both {@code market} and
     *     {@code eventId} filters are set
     */
    public Map<String, String> toQueryParams() {
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("user is required for a positions request");
        }
        boolean hasMarkets = markets != null && !markets.isEmpty();
        boolean hasEvents = eventIds != null && !eventIds.isEmpty();
        if (hasMarkets && hasEvents) {
            throw new IllegalStateException("market and eventId filters are mutually exclusive");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("user", user);
        if (hasMarkets)             params.put("market",        String.join(",", markets));
        if (hasEvents)              params.put("eventId",       String.join(",", eventIds));
        // toPlainString, never toString: a threshold must never reach the API in scientific notation.
        if (sizeThreshold != null)  params.put("sizeThreshold", sizeThreshold.toPlainString());
        if (redeemable != null)     params.put("redeemable",    String.valueOf(redeemable));
        if (mergeable != null)      params.put("mergeable",     String.valueOf(mergeable));
        if (limit != null)          params.put("limit",         String.valueOf(limit));
        if (offset != null)         params.put("offset",        String.valueOf(offset));
        if (sortBy != null)         params.put("sortBy",        sortBy);
        if (sortDirection != null)  params.put("sortDirection", sortDirection);
        if (title != null)          params.put("title",         title);
        return params;
    }
}
