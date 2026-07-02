package com.polymarket.ctf;

/** Response from a NegRisk redeem on-chain transaction. */
public record RedeemNegRiskResponse(
    /** Transaction hash (0x-prefixed hex). */
    String transactionHash,
    /** Block number in which the transaction was mined. */
    long blockNumber
) {}
