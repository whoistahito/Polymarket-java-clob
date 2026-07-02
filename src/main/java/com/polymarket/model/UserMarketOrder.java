package com.polymarket.model;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * Simplified structure for creating market orders.
 *
 * @param tokenID    Token ID of the Conditional token asset being traded
 * @param price      Price used to create the order. If it is not present the market price will be used.
 * @param amount     BUY orders: $$$ Amount to buy. SELL orders: Shares to sell.
 * @param side       Side of the order (BUY/SELL)
 * @param feeRateBps Fee rate, in basis points, charged to the order maker, charged on proceeds
 * @param nonce      Nonce used for onchain cancellations
 * @param taker      Address of the order taker. The zero address is used to indicate a public order
 * @param orderType  Specifies the type of order execution (FOK or FAK)
 */
@Builder
public record UserMarketOrder(
    String tokenID,
    BigDecimal price,
    BigDecimal amount,
    Side side,
    Integer feeRateBps,
    Integer nonce,
    String taker,
    OrderType orderType
) {}
