package com.polymarket.ctf;

import lombok.Builder;
import lombok.Getter;

/**
 * Request to calculate a position ID (ERC1155 token ID).
 *
 * <p>Position IDs are derived as:
 * {@code uint256(keccak256(encodePacked(collateralToken, collectionId)))}.
 */
@Getter
@Builder
public final class PositionIdRequest {

    /** Collateral token address, e.g. USDC (0x-prefixed hex). */
    private final String collateralToken;

    /** Collection ID (bytes32 0x-prefixed hex). */
    private final String collectionId;
}
