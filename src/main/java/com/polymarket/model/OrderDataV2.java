package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;
import lombok.Builder;
import lombok.Value;

/**
 * Raw V2 order input mirroring the V2 {@code sol!} signed struct.
 *
 * <p>Ground truth: {@code rs-clob-client/src/clob/types/mod.rs} module {@code v2}. The V2 signed
 * struct drops V1's {@code taker}, {@code expiration}, {@code nonce} and {@code feeRateBps} and adds
 * {@code timestamp}, {@code metadata} and {@code builder}. In V2 the {@code expiration} travels on
 * the outer JSON payload, not inside the signed struct, so it is intentionally absent here.
 *
 * <p>Field order mirrors the Rust struct: {@code salt, maker, signer, tokenId, makerAmount,
 * takerAmount, side, signatureType, timestamp, metadata, builder}.
 */
@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDataV2 {

    /** The 32-byte zero hash ({@code bytes32(0)}): {@code 0x} followed by 64 zero hex chars. */
    public static final String BYTES32_ZERO =
        "0x0000000000000000000000000000000000000000000000000000000000000000";

    /** Random salt ensuring order uniqueness. */
    BigInteger salt;

    /** Address of the order maker. Required. */
    String maker;

    /** Address of the key signing the order. Defaults to the maker address when null. */
    String signer;

    /** CTF ERC1155 token ID. Required. */
    @JsonProperty("tokenId")
    String tokenId;

    /** Maker amount in blockchain units (USDC × 10^6). Required. */
    @JsonProperty("makerAmount")
    BigInteger makerAmount;

    /** Taker amount in blockchain units. Required. */
    @JsonProperty("takerAmount")
    BigInteger takerAmount;

    /** BUY or SELL. Required. */
    Side side;

    /** Signature type. Defaults to {@link SignatureType#EOA} when null. */
    @JsonProperty("signatureType")
    SignatureType signatureType;

    /** Order timestamp as Unix seconds. */
    BigInteger timestamp;

    /** {@code bytes32} metadata. Defaults to {@link #BYTES32_ZERO} when unset. */
    @Builder.Default
    String metadata = BYTES32_ZERO;

    /** {@code bytes32} builder tag. Defaults to {@link #BYTES32_ZERO} when unset. */
    @Builder.Default
    String builder = BYTES32_ZERO;
}
