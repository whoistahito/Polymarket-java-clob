package com.polymarket.ws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Individual price level inside a {@link BookUpdate}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookLevel {

    /** Price at this level (as string to preserve precision). */
    private String price;

    /** Total size available at this price (as string to preserve precision). */
    private String size;
}
