package com.polymarket.ctf;

/** Response from a merge-positions on-chain transaction. */
public record MergePositionsResponse(
    /** Transaction hash (0x-prefixed hex). */
    String transactionHash,
    /** Block number in which the transaction was mined. */
    long blockNumber
) {}
