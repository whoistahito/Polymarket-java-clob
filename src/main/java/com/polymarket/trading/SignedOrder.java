package com.polymarket.trading;

import com.polymarket.markets.AssetId;
import java.util.Locale;
import lombok.NonNull;

/**
 * A fully signed V2 or V3 order, ready for the wire. {@code timestamp} is milliseconds for a
 * {@link com.polymarket.markets.TokenId} asset and seconds for a {@link com.polymarket.markets.PositionId}
 * one — the two exchanges document conflicting units and this type does not normalise them.
 * There is deliberately no nonce, fee-rate, taker, or version field: V1 cannot be expressed here.
 *
 * <p>{@code signer} is the address the exchange resolves for the order — the Trading Wallet itself
 * under signature type 3 — while {@code accountSigner} is the Account Signer that produced the
 * signature and authenticates the request. They coincide for every other wallet type.
 *
 * <p>Valid by construction, because submission takes this type rather than an Order Intent: a
 * hand-built value the signer could never have produced must not be able to reach {@code POST /order}.
 */
public record SignedOrder(long salt, @NonNull String maker, @NonNull String signer,
        @NonNull String accountSigner, @NonNull AssetId asset, @NonNull Side side,
        int signatureType, long makerAmount, long takerAmount, long timestamp,
        @NonNull String metadata, @NonNull String builder, @NonNull String signature) {

    /** Official signature types: 0 EOA, 1 Proxy, 2 Safe, 3 Deposit Wallet. */
    private static final int MAX_SIGNATURE_TYPE = 3;

    public SignedOrder {
        maker = requireAddress(maker, "maker");
        signer = requireAddress(signer, "signer");
        accountSigner = requireAddress(accountSigner, "accountSigner");
        requireUnsigned(salt, "salt");
        requireUnsigned(timestamp, "timestamp");
        if (makerAmount <= 0 || takerAmount <= 0) {
            throw new IllegalArgumentException("an order leg worth nothing cannot be submitted: "
                    + makerAmount + " for " + takerAmount);
        }
        if (signatureType < 0 || signatureType > MAX_SIGNATURE_TYPE) {
            throw new IllegalArgumentException(
                    "signatureType " + signatureType + " is not an official Polymarket wallet type");
        }
        metadata = requireBytes32(metadata, "metadata");
        builder = requireBytes32(builder, "builder");
        if (!signature.matches("(?i)0x(?:[0-9a-f]{2})+")) {
            throw new IllegalArgumentException(
                    "signature must be 0x-prefixed hex, got: " + signature);
        }
    }

    private static String requireAddress(String value, String field) {
        if (!value.matches("(?i)0x[0-9a-f]{40}")) {
            throw new IllegalArgumentException(
                    field + " must be a 20-byte hex address, got: " + value);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String requireBytes32(String value, String field) {
        if (!value.matches("(?i)0x[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    field + " must be a 32-byte hex value, got: " + value);
        }
        return value;
    }

    private static void requireUnsigned(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " is unsigned on the wire, got " + value);
        }
    }
}
