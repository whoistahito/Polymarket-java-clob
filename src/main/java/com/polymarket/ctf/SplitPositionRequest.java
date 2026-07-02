package com.polymarket.ctf;

import java.math.BigInteger;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to split USDC collateral into outcome token pairs (YES/NO).
 */
@Getter
@Builder
public final class SplitPositionRequest {

    /**
     * Standard binary-market partition: YES (index set 0b01 = 1) and NO (index set 0b10 = 2).
     */
    public static final List<BigInteger> BINARY_PARTITION = List.of(BigInteger.ONE, BigInteger.TWO);

    /** Collateral token address, e.g. USDC (0x-prefixed hex). */
    private final String collateralToken;

    /** Parent collection ID (bytes32 0x-prefixed hex). Typically the zero hash for Polymarket. */
    @Builder.Default
    private final String parentCollectionId = "0x" + "0".repeat(64);

    /** Condition ID to split on (bytes32 0x-prefixed hex). */
    private final String conditionId;

    /**
     * Disjoint index sets representing outcome slots.
     * For binary markets: {@code [1, 2]} (YES = 0b01, NO = 0b10).
     */
    private final List<BigInteger> partition;

    /** Amount of collateral to split (in token base units, e.g. 6 decimals for USDC). */
    private final BigInteger amount;

    /**
     * Creates a split request pre-configured for a standard binary YES/NO market.
     *
     * @param collateralToken collateral token address (0x-prefixed hex)
     * @param conditionId     condition ID (bytes32 0x-prefixed hex)
     * @param amount          amount of collateral to split (in token base units)
     * @return a ready-to-use {@link SplitPositionRequest}
     */
    public static SplitPositionRequest forBinaryMarket(
        String collateralToken, String conditionId, BigInteger amount
    ) {
        return SplitPositionRequest.builder()
            .collateralToken(collateralToken)
            .conditionId(conditionId)
            .partition(BINARY_PARTITION)
            .amount(amount)
            .build();
    }
}
