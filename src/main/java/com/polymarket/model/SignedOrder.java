package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;

/**
 * Signed order sent to the exchange. Version-tagged (Rust-style {@code OrderPayload}):
 *
 * <ul>
 *   <li><b>V1</b> ({@code version == 1}) — legacy struct. Signs {@code taker}/{@code nonce}/
 *       {@code feeRateBps} into the EIP-712 hash; domain version {@code "1"} against the V1 exchange
 *       (or neg-risk V1) contract. Serialized with all V1 fields.</li>
 *   <li><b>V2</b> ({@code version == 2}, the default when unset) — V2 struct signs
 *       {@code timestamp}/{@code metadata}/{@code builder} and omits {@code taker}/{@code nonce}/
 *       {@code feeRateBps} from the hash; domain version {@code "2"} against the V2 exchange (or
 *       neg-risk V2) contract. Serialized with the V2 field shape (no {@code taker}/{@code nonce}/
 *       {@code feeRateBps}; {@code expiration} is kept on the payload, not the signed struct).</li>
 * </ul>
 *
 * <p>Ground truth: {@code rs-clob-client/src/clob/types/mod.rs} ({@code OrderPayload} enum +
 * {@code OrderV1WithSignature}/{@code OrderV2WithSignature} serializers) and
 * {@code rs-clob-client/src/clob/client.rs} (domain/verifying-contract selection).
 *
 * <p>Serialization is delegated to {@link SignedOrderSerializer}, which emits the V1 or V2 shape
 * based on {@link #version()}.
 *
 * @param salt          Random number ensuring uniqueness (IEEE-754 masked).
 * @param maker         Address of the order maker.
 * @param signer        Address of the key signing the order.
 * @param taker         V1 only — address of the order taker (zero address = public order). Omitted on V2.
 * @param tokenId       CTF ERC1155 token ID.
 * @param makerAmount   Maker amount in raw units (USDC × 10^6).
 * @param takerAmount   Taker amount in raw units.
 * @param expiration    Timestamp after which the order is expired. In V1 this is part of the signed
 *                      struct; in V2 it rides the outer payload (still serialized here).
 * @param nonce         V1 only — on-chain cancel nonce. Omitted on V2.
 * @param feeRateBps    V1 only — maker fee rate in basis points. Omitted on V2.
 * @param side          Direction of the trade (BUY/SELL).
 * @param signatureType EIP-712 signature type. {@link SignatureType#POLY_1271} is V2-only.
 * @param signature     The cryptographic signature (ECDSA hex, or POLY_1271 wrapped form).
 * @param version       Protocol version (1 or 2). Null ⟹ 2 (V2 is the default).
 * @param timestamp     V2 only — Unix-milliseconds timestamp in the signed struct.
 * @param metadata      V2 only — {@code bytes32} metadata tag (hex {@code 0x...}); defaults to
 *                      {@code bytes32(0)}. Omitted on V1.
 * @param builderCode   V2 only — {@code bytes32} builder tag (hex {@code 0x...}); defaults to
 *                      {@code bytes32(0)}. Serialized as {@code "builder"}. Omitted on V1.
 */
@Builder
@JsonSerialize(using = SignedOrderSerializer.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    String signature,
    Integer version,
    String timestamp,
    String metadata,
    @JsonProperty("builder") String builderCode
) {

    /** Resolved protocol version — null defaults to 2 (V2 is the server default). */
    public int resolvedVersion() {
        return version != null ? version : 2;
    }

    /** Convenience builder override that fills V1-only fields with safe defaults for V2 orders. */
    public static SignedOrderBuilder v2Builder() {
        return builder()
            .version(2)
            .taker(null)
            .nonce("0")
            .feeRateBps("0")
            .expiration("0");
    }
}