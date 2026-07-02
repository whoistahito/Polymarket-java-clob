package com.polymarket.ctf;

/** Response from a collection ID calculation. */
public record CollectionIdResponse(
    /** Computed collection ID (bytes32 0x-prefixed hex). */
    String collectionId
) {}
