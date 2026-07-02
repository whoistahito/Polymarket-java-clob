package com.polymarket.model;

import lombok.Builder;

/**
 * Represents the signed order structure sent to the exchange.
 *
 * @param salt          Random number to ensure uniqueness
 * @param maker         Address of the order maker
 * @param signer        Address of the key signing the order
 * @param taker         Address of the order taker (0x0 for public orders)
 * @param tokenId       Token ID of the outcome
 * @param makerAmount   Amount of collateral being made (in wei)
 * @param takerAmount   Amount of collateral being taken (in wei)
 * @param expiration    Timestamp after which the order is expired
 * @param nonce         Nonce used for onchain cancellations
 * @param feeRateBps    Fee rate in basis points
 * @param side          Direction of the trade (BUY/SELL)
 * @param signatureType EIP-712 signature type
 * @param signature     The cryptographic signature
 */
@Builder
public record SignedOrder(
    long salt,
    String maker,
    String signer,
    String taker,
    String tokenId,
    String makerAmount,
    String takerAmount,
    String expiration,
    String nonce,
    String feeRateBps,
    Side side,
    SignatureType signatureType,
    String signature
) {}
