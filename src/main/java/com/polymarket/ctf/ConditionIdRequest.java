package com.polymarket.ctf;

import java.math.BigInteger;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to calculate a condition ID.
 *
 * <p>The condition ID is derived from the oracle address, question hash, and number of outcome
 * slots: {@code keccak256(encodePacked(oracle, questionId, outcomeSlotCount))}.
 */
@Getter
@Builder
public final class ConditionIdRequest {

    /** Oracle address that will report the outcome (0x-prefixed hex). */
    private final String oracle;

    /** Question ID (bytes32 0x-prefixed hex). */
    private final String questionId;

    /** Number of outcome slots. Typically 2 for binary (YES/NO) markets. */
    private final BigInteger outcomeSlotCount;
}
