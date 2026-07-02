package com.polymarket.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Query parameters for listing RFQ quotes.
 * Mirrors TS {@code GetRfqQuotesParams}.
 */
@Value
@Builder
public class GetRfqQuotesParams {

    String offset;
    Integer limit;
    String state;
    List<String> quoteIds;
    List<String> requestIds;
    List<String> markets;
    Double sizeMin;
    Double sizeMax;
    Double sizeUsdcMin;
    Double sizeUsdcMax;
    Double priceMin;
    Double priceMax;
    String sortBy;
    String sortDir;
}
