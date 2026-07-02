package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * Simplified structure for creating new orders.
 *
 * @param tokenID     Token ID of the Conditional token asset being traded
 * @param price       Price used to create the order
 * @param size        Size in terms of the ConditionalToken
 * @param side        Side of the order (BUY/SELL)
 * @param feeRateBps  Fee rate, in basis points, charged to the order maker, charged on proceeds
 * @param nonce       Nonce used for onchain cancellations
 * @param expiration  Timestamp after which the order is expired
 * @param taker       Address of the order taker. The zero address is used to indicate a public order
 * @param metadata    V2-only {@code bytes32} metadata tag (hex {@code 0x...}); defaults to
 *                    {@code bytes32(0)} when null. Ignored on V1.
 * @param builderCode V2-only {@code bytes32} builder tag (hex {@code 0x...}); defaults to
 *                    {@code bytes32(0)} when null. Ignored on V1. Wire field is {@code "builder"}.
 */
@Builder
public record UserOrder(
    String tokenID,
    BigDecimal price,
    BigDecimal size,
    Side side,
    Integer feeRateBps,
    Integer nonce,
    Long expiration,
    String taker,
    String metadata,
    @JsonProperty("builder") String builderCode
) {}
