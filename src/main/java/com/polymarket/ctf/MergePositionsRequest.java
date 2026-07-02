package com.polymarket.ctf;

import java.math.BigInteger;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to merge outcome token pairs back into USDC collateral.
 */
@Getter
@Builder
public final class MergePositionsRequest {

    /** Collateral token address, e.g. USDC (0x-prefixed hex). */
    private final String collateralToken;

    /** Parent collection ID (bytes32 0x-prefixed hex). Typically the zero hash for Polymarket. */
    @Builder.Default
    private final String parentCollectionId = "0x" + "0".repeat(64);

    /** Condition ID to merge on (bytes32 0x-prefixed hex). */
    private final String conditionId;

    /**
     * Disjoint index sets representing outcome slots.
     * For binary markets: {@code [1, 2]} (YES = 0b01, NO = 0b10).
     */
    private final List<BigInteger> partition;

    /** Number of full outcome-token sets to merge back into collateral. */
    private final BigInteger amount;

    /**
     * Creates a merge request pre-configured for a standard binary YES/NO market.
     *
     * @param collateralToken collateral token address (0x-prefixed hex)
     * @param conditionId     condition ID (bytes32 0x-prefixed hex)
     * @param amount          number of full sets to merge
     * @return a ready-to-use {@link MergePositionsRequest}
     */
    public static MergePositionsRequest forBinaryMarket(
        String collateralToken, String conditionId, BigInteger amount
    ) {
        return MergePositionsRequest.builder()
            .collateralToken(collateralToken)
            .conditionId(conditionId)
            .partition(SplitPositionRequest.BINARY_PARTITION)
            .amount(amount)
            .build();
    }
}
