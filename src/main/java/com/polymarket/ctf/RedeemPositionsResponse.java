package com.polymarket.ctf;

/** Response from a redeem-positions on-chain transaction. */
public record RedeemPositionsResponse(
    /** Transaction hash (0x-prefixed hex). */
    String transactionHash,
    /** Block number in which the transaction was mined. */
    long blockNumber
) {}
