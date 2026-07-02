package com.polymarket.client;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Level 1 (L1) EIP-712 signer compatible with Polymarket's clob-client.
 *
 * <p>Compatibility notes (mirrors clob-client's signing/eip712.ts):
 * <ul>
 *   <li>Domain: name="ClobAuthDomain", version="1", chainId=chain</li>
 *   <li>Message: address, timestamp (string), nonce (uint256), message (fixed string)</li>
 *   <li>Fixed message: "This message attests that I control the given wallet"</li>
 * </ul>
 */
public final class L1Eip712Signer {

    /** The fixed message to sign, matching py-clob-client. */
    public static final String MSG_TO_SIGN = "This message attests that I control the given wallet";

    private final Clock clock;

    public L1Eip712Signer() {
        this(Clock.systemUTC());
    }

    public L1Eip712Signer(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates L1 headers for API key derivation/creation.
     *
     * @param credentials Web3j credentials containing the private key
     * @param chainId     Polygon chain ID (137 for mainnet, 80002 for Amoy)
     * @param nonce       Nonce for the request (default 0)
     * @return Map of headers (POLY_ADDRESS, POLY_SIGNATURE, POLY_TIMESTAMP, POLY_NONCE)
     */
    public Map<String, String> createL1Headers(Credentials credentials, int chainId, int nonce) throws IOException {
        long timestamp = clock.instant().getEpochSecond();
        return createL1Headers(credentials, chainId, nonce, timestamp);
    }

    /**
     * Creates L1 headers with explicit timestamp.
     */
    public Map<String, String> createL1Headers(Credentials credentials, int chainId, int nonce, long timestamp) throws IOException {
        String address = credentials.getAddress();
        String signature = signEip712(credentials, chainId, timestamp, nonce);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(PolymarketEndpoints.HDR_POLY_ADDRESS, address);
        headers.put(PolymarketEndpoints.HDR_POLY_SIGNATURE, signature);
        headers.put(PolymarketEndpoints.HDR_POLY_TIMESTAMP, String.valueOf(timestamp));
        headers.put(PolymarketEndpoints.HDR_POLY_NONCE, String.valueOf(nonce));
        return headers;
    }

    /**
     * Sign an EIP-712 structured message for L1 authentication.
     */
    public String signEip712(Credentials credentials, int chainId, long timestamp, int nonce) throws IOException {
        String address = credentials.getAddress();
        String jsonMessage = buildEip712Json(address, chainId, timestamp, nonce);

        StructuredDataEncoder encoder = new StructuredDataEncoder(jsonMessage);
        byte[] hash = encoder.hashStructuredData();

        Sign.SignatureData signatureData = Sign.signMessage(hash, credentials.getEcKeyPair(), false);

        // Convert to hex string with 0x prefix
        return toHexString(signatureData);
    }

    /**
     * Build the EIP-712 structured data JSON.
     */
    private String buildEip712Json(String address, int chainId, long timestamp, int nonce) {
        // Must match the exact structure expected by clob-client
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
            """.formatted(chainId, address, timestamp, nonce, MSG_TO_SIGN);
    }

    /**
     * Convert signature data to a hex string with 0x prefix.
     * Format: r (32 bytes) + s (32 bytes) + v (1 byte)
     */
    private String toHexString(Sign.SignatureData signatureData) {
        byte[] r = signatureData.getR();
        byte[] s = signatureData.getS();
        byte[] v = signatureData.getV();

        // Total 65 bytes: 32 (r) + 32 (s) + 1 (v)
        byte[] combined = new byte[65];
        System.arraycopy(r, 0, combined, 0, 32);
        System.arraycopy(s, 0, combined, 32, 32);
        combined[64] = v[0];

        StringBuilder sb = new StringBuilder("0x");
        for (byte b : combined) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Small value object for L1 signature result.
     */
    public record SignedL1Payload(long timestampSeconds, int nonce, String signature) {}
}
