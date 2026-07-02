package com.polymarket.ctf;

/** Response from a split-position on-chain transaction. */
public record SplitPositionResponse(
    /** Transaction hash (0x-prefixed hex). */
    String transactionHash,
    /** Block number in which the transaction was mined. */
    long blockNumber
) {}
