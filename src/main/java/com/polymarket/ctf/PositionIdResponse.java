package com.polymarket.ctf;

import java.math.BigInteger;

/** Response from a position ID calculation. */
public record PositionIdResponse(
    /** Computed position ID (ERC1155 token ID as uint256). */
    BigInteger positionId
) {}
