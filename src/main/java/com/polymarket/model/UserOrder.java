package com.polymarket.model;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * Simplified structure for creating new orders.
 *
 * @param tokenID    Token ID of the Conditional token asset being traded
 * @param price      Price used to create the order
 * @param size       Size in terms of the ConditionalToken
 * @param side       Side of the order (BUY/SELL)
 * @param feeRateBps Fee rate, in basis points, charged to the order maker, charged on proceeds
 * @param nonce      Nonce used for onchain cancellations
 * @param expiration Timestamp after which the order is expired
 * @param taker      Address of the order taker. The zero address is used to indicate a public order
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
    String taker
) {}
