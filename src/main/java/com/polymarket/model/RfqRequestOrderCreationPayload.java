package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Payload describing the order being created as part of an RFQ request.
 *
 * <p>Mirrors the TypeScript {@code RfqRequestOrderCreationPayload} interface in
 * {@code clob-client/src/types.ts}:
 * <pre>{@code
 * interface RfqRequestOrderCreationPayload {
 *     token: string;   side: Side;   size: string;   price: number;
 * }
 * }</pre>
 */
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqRequestOrderCreationPayload {

    /** Token ID of the outcome being traded. */
    String token;

    /** Order side (BUY or SELL). */
    Side side;

    /** Order size as a decimal string. */
    String size;

    /** Order price. */
    BigDecimal price;
}
