package com.polymarket.internal.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.client.L1Eip712Signer;
import com.polymarket.client.L2HmacSigner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

/**
 * Signing is protocol-critical, so the relocated 2.0 attestations are held byte-identical
 * to the 1.0 implementations they replace.
 */
@DisplayName("2.0 attestations match the 1.0 signers exactly")
class AttestationEquivalenceTest {

    private static final String TEST_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final long TIMESTAMP = 1773890758L;
    private static final int CHAIN_ID = 137;

    @Test
    @DisplayName("TC-AE-001: L1 headers are identical to the 1.0 EIP-712 signer's")
    void l1HeadersMatchTheIncumbent() throws Exception {
        Credentials credentials = Credentials.create(TEST_KEY);
        Map<String, String> incumbent = new L1Eip712Signer(
                Clock.fixed(Instant.ofEpochSecond(TIMESTAMP), ZoneOffset.UTC))
                .createL1Headers(credentials, CHAIN_ID, 0);

        Map<String, String> relocated = L1Attestation.headers(
                PrivateKeySigner.of(TEST_KEY), CHAIN_ID, TIMESTAMP, 0);

        assertEquals(incumbent.get("POLY_SIGNATURE"), relocated.get("POLY_SIGNATURE"));
        assertEquals(incumbent.get("POLY_TIMESTAMP"), relocated.get("POLY_TIMESTAMP"));
        assertEquals(incumbent.get("POLY_NONCE"), relocated.get("POLY_NONCE"));
        assertEquals(incumbent.get("POLY_ADDRESS").toLowerCase(), relocated.get("POLY_ADDRESS"));
    }

    @Test
    @DisplayName("TC-AE-002: the L2 HMAC is identical to the 1.0 signer's")
    void l2SignatureMatchesTheIncumbent() {
        String secret = "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==";

        assertEquals(
                new L2HmacSigner().sign(secret, TIMESTAMP, "GET", "/auth/api-keys", null),
                L2Attestation.sign(secret, TIMESTAMP, "GET", "/auth/api-keys", null));
        assertEquals(
                new L2HmacSigner().sign(secret, TIMESTAMP, "POST", "/order", "{\"a\":1}"),
                L2Attestation.sign(secret, TIMESTAMP, "POST", "/order", "{\"a\":1}"));
    }
}
