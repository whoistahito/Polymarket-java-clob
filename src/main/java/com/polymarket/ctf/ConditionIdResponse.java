package com.polymarket.ctf;

/** Response from a condition ID calculation. */
public record ConditionIdResponse(
    /** Computed condition ID (bytes32 0x-prefixed hex). */
    String conditionId
) {}
