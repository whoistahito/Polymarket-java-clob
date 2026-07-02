package com.polymarket.ctf;

import java.math.BigInteger;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to redeem positions using the NegRisk adapter.
 *
 * <p>Used for negative-risk markets where redemption specifies per-outcome amounts rather than
 * index sets.
 */
@Getter
@Builder
public final class RedeemNegRiskRequest {

    /** Condition ID to redeem (bytes32 0x-prefixed hex). */
    private final String conditionId;

    /**
     * Per-outcome token amounts to redeem.
     * For binary markets this list has two elements: {@code [yesAmount, noAmount]}.
     */
    private final List<BigInteger> amounts;
}
