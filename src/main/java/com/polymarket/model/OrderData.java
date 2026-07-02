package com.polymarket.model;

import java.math.BigInteger;
import lombok.Builder;
import lombok.Value;

/**
 * Raw order input for {@code OrderUtils} standalone builder.
 *
 * <p>Holds pre-calculated maker/taker amounts in blockchain units (scaled ×10⁶ for USDC).
 * This is the Java equivalent of the raw order struct accepted by Python's {@code py-order-utils}.
 */
@Value
@Builder
public class OrderData {

    /** CTF ERC1155 token ID. Required. */
    String tokenId;

    /** BUY or SELL. Required. */
    Side side;

    /** Maker amount in blockchain units (USDC × 10^6). Required. */
    BigInteger makerAmount;

    /** Taker amount in blockchain units. Required. */
    BigInteger takerAmount;

    /** Fee rate in basis points, e.g. {@code BigInteger.valueOf(100)} = 1%. Required. */
    BigInteger feeRateBps;

    /** Order nonce. Defaults to {@link BigInteger#ZERO} when null. */
    BigInteger nonce;

    /** Expiration as Unix timestamp. {@link BigInteger#ZERO} means no expiry. Defaults to zero when null. */
    BigInteger expiration;

    /** Public taker address. {@code null} or zero address means anyone can fill. */
    String taker;

    /** Signature type. Defaults to {@link SignatureType#EOA} when null. */
    SignatureType signatureType;

    /** Optional signer address override. Defaults to the maker address when null. */
    String signer;
}
