package com.polymarket.internal.authentication;

import com.polymarket.authentication.PrivateKeySigner;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.web3j.crypto.StructuredDataEncoder;

/**
 * Builds the CLOB L1 headers. The EIP-712 digest is assembled here and handed to the
 * signing authority, so the private key never leaves the domain type.
 */
public final class L1Attestation {

    public static final String MESSAGE = "This message attests that I control the given wallet";

    private L1Attestation() {
    }

    public static Map<String, String> headers(
            PrivateKeySigner signer, int chainId, long timestampSeconds, int nonce)
            throws IOException {
        String signature = signer.sign(digest(signer.address(), chainId, timestampSeconds, nonce));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("POLY_ADDRESS", signer.address());
        headers.put("POLY_SIGNATURE", signature);
        headers.put("POLY_TIMESTAMP", String.valueOf(timestampSeconds));
        headers.put("POLY_NONCE", String.valueOf(nonce));
        return headers;
    }

    static byte[] digest(String address, int chainId, long timestampSeconds, int nonce)
            throws IOException {
        return new StructuredDataEncoder(json(address, chainId, timestampSeconds, nonce))
                .hashStructuredData();
    }

    private static String json(String address, int chainId, long timestampSeconds, int nonce) {
        return """
            {
                "types": {
                    "EIP712Domain": [
                        {"name": "name", "type": "string"},
                        {"name": "version", "type": "string"},
                        {"name": "chainId", "type": "uint256"}
                    ],
                    "ClobAuth": [
                        {"name": "address", "type": "address"},
                        {"name": "timestamp", "type": "string"},
                        {"name": "nonce", "type": "uint256"},
                        {"name": "message", "type": "string"}
                    ]
                },
                "primaryType": "ClobAuth",
                "domain": {
                    "name": "ClobAuthDomain",
                    "version": "1",
                    "chainId": %d
                },
                "message": {
                    "address": "%s",
                    "timestamp": "%d",
                    "nonce": %d,
                    "message": "%s"
                }
            }
            """.formatted(chainId, address, timestampSeconds, nonce, MESSAGE);
    }
}
