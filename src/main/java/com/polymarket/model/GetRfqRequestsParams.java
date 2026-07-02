package com.polymarket.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Query parameters for listing RFQ requests.
 * Mirrors TS {@code GetRfqRequestsParams}.
 */
@Value
@Builder
public class GetRfqRequestsParams {

    String offset;
    Integer limit;
    String state;
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
