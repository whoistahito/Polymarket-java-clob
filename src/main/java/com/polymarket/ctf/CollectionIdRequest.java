package com.polymarket.ctf;

import java.math.BigInteger;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to calculate a collection ID.
 *
 * <p>Collection IDs are derived via XOR:
 * {@code parentCollectionId XOR keccak256(encodePacked(conditionId, indexSet))}.
 */
@Getter
@Builder
public final class CollectionIdRequest {

    /**
     * Parent collection ID (bytes32 0x-prefixed hex).
     * Typically the zero hash for top-level positions.
     */
    @Builder.Default
    private final String parentCollectionId = "0x" + "0".repeat(64);

    /** Condition ID (bytes32 0x-prefixed hex). */
    private final String conditionId;

    /**
     * Index set representing outcome slots.
     * For YES outcome: {@code 1} (0b01); for NO outcome: {@code 2} (0b10).
     */
    private final BigInteger indexSet;
}
