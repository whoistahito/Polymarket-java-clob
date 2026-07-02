package com.polymarket.ctf;

import java.math.BigInteger;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to redeem winning outcome tokens for USDC collateral after market resolution.
 */
@Getter
@Builder
public final class RedeemPositionsRequest {

    /** Collateral token address, e.g. USDC (0x-prefixed hex). */
    private final String collateralToken;

    /** Parent collection ID (bytes32 0x-prefixed hex). Typically the zero hash for Polymarket. */
    @Builder.Default
    private final String parentCollectionId = "0x" + "0".repeat(64);

    /** Condition ID to redeem (bytes32 0x-prefixed hex). */
    private final String conditionId;

    /**
     * Index sets representing the outcome slots to redeem.
     * For binary markets: {@code [1, 2]}.
     */
    private final List<BigInteger> indexSets;

    /**
     * Creates a redeem request pre-configured for a standard binary YES/NO market.
     *
     * @param collateralToken collateral token address (0x-prefixed hex)
     * @param conditionId     condition ID (bytes32 0x-prefixed hex)
     * @return a ready-to-use {@link RedeemPositionsRequest}
     */
    public static RedeemPositionsRequest forBinaryMarket(
        String collateralToken, String conditionId
    ) {
        return RedeemPositionsRequest.builder()
            .collateralToken(collateralToken)
            .conditionId(conditionId)
            .indexSets(SplitPositionRequest.BINARY_PARTITION)
            .build();
    }
}
