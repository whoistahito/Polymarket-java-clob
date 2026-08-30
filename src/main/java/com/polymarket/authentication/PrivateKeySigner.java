package com.polymarket.authentication;

import lombok.NonNull;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;

/**
 * An Account Signer's local key. Holds the private key and derives its address offline;
 * {@code toString} never discloses the key.
 */
public final class PrivateKeySigner {

    private final String privateKey;
    private final String address;

    private PrivateKeySigner(String privateKey, String address) {
        this.privateKey = privateKey;
        this.address = address;
    }

    public static PrivateKeySigner of(@NonNull String hexPrivateKey) {
        String normalized = hexPrivateKey.startsWith("0x") || hexPrivateKey.startsWith("0X")
                ? hexPrivateKey.substring(2) : hexPrivateKey;
        if (!normalized.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("private key must be 32 hex bytes");
        }
        return new PrivateKeySigner(normalized, Addresses.fromPrivateKey(normalized));
    }

    public String address() {
        return address;
    }

    /**
     * Signs a 32-byte EIP-712 digest, returning r+s+v as 0x-prefixed hex. The key is never
     * handed out, so callers can sign without being able to exfiltrate it.
     */
    public String sign(byte[] digest32) {
        if (digest32 == null || digest32.length != 32) {
            throw new IllegalArgumentException("an EIP-712 digest is 32 bytes");
        }
        Sign.SignatureData signature = Sign.signMessage(
                digest32, Credentials.create(privateKey).getEcKeyPair(), false);

        byte[] combined = new byte[65];
        System.arraycopy(signature.getR(), 0, combined, 0, 32);
        System.arraycopy(signature.getS(), 0, combined, 32, 32);
        combined[64] = signature.getV()[0];

        StringBuilder hex = new StringBuilder("0x");
        for (byte b : combined) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Override
    public String toString() {
        return "PrivateKeySigner[address=" + address + ", privateKey=***]";
    }
}
