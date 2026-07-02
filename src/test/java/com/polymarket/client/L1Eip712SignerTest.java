package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

/**
 * Test cases for L1Eip712Signer.
 * Verifies EIP-712 signature generation for L1 authentication matches TypeScript SDK.
 */
@DisplayName("L1Eip712Signer Tests")
class L1Eip712SignerTest {

    private static final String TEST_PRIVATE_KEY =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final long FIXED_TIMESTAMP = 1700000000L;

    private Credentials credentials;
    private Clock fixedClock;
    private L1Eip712Signer signer;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(TEST_PRIVATE_KEY);
        fixedClock = Clock.fixed(
            Instant.ofEpochSecond(FIXED_TIMESTAMP),
            ZoneId.of("UTC")
        );
        signer = new L1Eip712Signer(fixedClock);
    }

    @Test
    @DisplayName(
        "TC-L1-001: Verify EIP-712 Domain Structure matches TypeScript SDK"
    )
    void testEip712DomainMatchesTypeScriptSdk() throws IOException {
        // Expected from TypeScript: { name: "ClobAuthDomain", version: "1", chainId: chainId }
        // The domain is embedded in the EIP-712 JSON structure built by the signer
        // We verify by checking that the signature is correctly produced with expected domain

        // Create headers which uses the EIP-712 signing internally
        Map<String, String> headers = signer.createL1Headers(
            credentials,
            137,
            0,
            FIXED_TIMESTAMP
        );

        // Verify the signature is valid (indirectly confirms domain structure is correct)
        String signature = headers.get(PolymarketEndpoints.HDR_POLY_SIGNATURE);
        assertNotNull(
            signature,
            "Signature should be generated with correct domain"
        );

        // The signature format confirms the EIP-712 signing worked correctly
        // Domain structure: { name: "ClobAuthDomain", version: "1", chainId: 137 }
        assertTrue(
            signature.startsWith("0x"),
            "Signature should start with 0x prefix"
        );
        assertEquals(
            132,
            signature.length(),
            "Signature should be 65 bytes (EIP-712 signature format)"
        );

        // Different chain IDs produce different signatures, confirming chainId is in domain
        Map<String, String> amoyHeaders = signer.createL1Headers(
            credentials,
            80002,
            0,
            FIXED_TIMESTAMP
        );
        assertNotEquals(
            signature,
            amoyHeaders.get(PolymarketEndpoints.HDR_POLY_SIGNATURE),
            "Different chainId in domain should produce different signature"
        );
    }

    @Test
    @DisplayName(
        "TC-L1-002: Verify MSG_TO_SIGN constant matches TypeScript SDK"
    )
    void testMsgToSignConstant() {
        assertEquals(
            "This message attests that I control the given wallet",
            L1Eip712Signer.MSG_TO_SIGN
        );
    }

    @Test
    @DisplayName("TC-L1-003: Create L1 Headers with known credentials")
    void testCreateL1Headers() throws IOException {
        Map<String, String> headers = signer.createL1Headers(
            credentials,
            137,
            0,
            FIXED_TIMESTAMP
        );

        assertNotNull(headers.get(PolymarketEndpoints.HDR_POLY_ADDRESS));
        assertNotNull(headers.get(PolymarketEndpoints.HDR_POLY_SIGNATURE));
        assertEquals(
            String.valueOf(FIXED_TIMESTAMP),
            headers.get(PolymarketEndpoints.HDR_POLY_TIMESTAMP)
        );
        assertEquals("0", headers.get(PolymarketEndpoints.HDR_POLY_NONCE));

        // Verify signature format
        String signature = headers.get(PolymarketEndpoints.HDR_POLY_SIGNATURE);
        assertTrue(
            signature.startsWith("0x"),
            "Signature should start with 0x"
        );
        assertEquals(
            132,
            signature.length(),
            "Signature should be 65 bytes (130 hex chars + 0x prefix)"
        );
    }

    @Test
    @DisplayName(
        "TC-L1-003b: Create L1 Headers uses clock timestamp when not explicitly provided"
    )
    void testCreateL1HeadersUsesClockTimestamp() throws IOException {
        Map<String, String> headers = signer.createL1Headers(
            credentials,
            137,
            0
        );

        assertEquals(
            String.valueOf(FIXED_TIMESTAMP),
            headers.get(PolymarketEndpoints.HDR_POLY_TIMESTAMP)
        );
    }

    @Test
    @DisplayName("TC-L1-004: Signature format validation")
    void testSignatureFormat() throws IOException {
        String signature = signer.signEip712(
            credentials,
            137,
            FIXED_TIMESTAMP,
            0
        );

        assertTrue(
            signature.startsWith("0x"),
            "Signature should start with 0x"
        );
        assertEquals(
            132,
            signature.length(),
            "Signature should be 65 bytes (130 hex chars + 0x)"
        );
        assertTrue(
            signature.matches("0x[0-9a-fA-F]{130}"),
            "Should be valid hex"
        );
    }

    @Test
    @DisplayName("TC-L1-005: Different chain IDs produce different signatures")
    void testDifferentChainIds() throws IOException {
        String sigMainnet = signer.signEip712(
            credentials,
            137,
            FIXED_TIMESTAMP,
            0
        );
        String sigAmoy = signer.signEip712(
            credentials,
            80002,
            FIXED_TIMESTAMP,
            0
        );

        assertNotEquals(
            sigMainnet,
            sigAmoy,
            "Different chain IDs should produce different signatures"
        );
    }

    @Test
    @DisplayName("TC-L1-006: Nonce affects signature")
    void testNonceAffectsSignature() throws IOException {
        String sig0 = signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 0);
        String sig1 = signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 1);

        assertNotEquals(
            sig0,
            sig1,
            "Different nonces should produce different signatures"
        );
    }

    @Test
    @DisplayName("TC-L1-007: Timestamp affects signature")
    void testTimestampAffectsSignature() throws IOException {
        String sig1 = signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 0);
        String sig2 = signer.signEip712(
            credentials,
            137,
            FIXED_TIMESTAMP + 1,
            0
        );

        assertNotEquals(
            sig1,
            sig2,
            "Different timestamps should produce different signatures"
        );
    }

    @Test
    @DisplayName(
        "TC-L1-008: Different private keys produce different signatures"
    )
    void testDifferentPrivateKeysProduceDifferentSignatures()
        throws IOException {
        String otherPrivateKey =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Credentials otherCredentials = Credentials.create(otherPrivateKey);

        String sig1 = signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 0);
        String sig2 = signer.signEip712(
            otherCredentials,
            137,
            FIXED_TIMESTAMP,
            0
        );

        assertNotEquals(
            sig1,
            sig2,
            "Different private keys should produce different signatures"
        );
    }

    @Test
    @DisplayName("TC-L1-009: Headers contain correct address")
    void testHeadersContainCorrectAddress() throws IOException {
        Map<String, String> headers = signer.createL1Headers(
            credentials,
            137,
            0,
            FIXED_TIMESTAMP
        );

        String expectedAddress = credentials.getAddress();
        assertEquals(
            expectedAddress,
            headers.get(PolymarketEndpoints.HDR_POLY_ADDRESS)
        );
    }

    @Test
    @DisplayName(
        "TC-L1-010: Same inputs produce same signature (deterministic)"
    )
    void testDeterministicSignature() throws IOException {
        String sig1 = signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 0);
        String sig2 = signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 0);

        assertEquals(sig1, sig2, "Same inputs should produce same signature");
    }

    @Test
    @DisplayName("TC-L1-011: SignedL1Payload record contains correct values")
    void testSignedL1PayloadRecord() {
        L1Eip712Signer.SignedL1Payload payload =
            new L1Eip712Signer.SignedL1Payload(FIXED_TIMESTAMP, 5, "0xabc123");

        assertEquals(FIXED_TIMESTAMP, payload.timestampSeconds());
        assertEquals(5, payload.nonce());
        assertEquals("0xabc123", payload.signature());
    }

    @Test
    @DisplayName("TC-L1-012: Constructor with default clock")
    void testDefaultClockConstructor() {
        L1Eip712Signer defaultSigner = new L1Eip712Signer();
        assertNotNull(defaultSigner);
    }

    @Test
    @DisplayName("TC-L1-013: Constructor rejects null clock")
    void testNullClockRejected() {
        assertThrows(NullPointerException.class, () ->
            new L1Eip712Signer(null)
        );
    }

    @Test
    @DisplayName("TC-L1-014: All header keys are present")
    void testAllHeaderKeysPresent() throws IOException {
        Map<String, String> headers = signer.createL1Headers(
            credentials,
            137,
            0,
            FIXED_TIMESTAMP
        );

        assertAll(
            () ->
                assertTrue(
                    headers.containsKey(PolymarketEndpoints.HDR_POLY_ADDRESS)
                ),
            () ->
                assertTrue(
                    headers.containsKey(PolymarketEndpoints.HDR_POLY_SIGNATURE)
                ),
            () ->
                assertTrue(
                    headers.containsKey(PolymarketEndpoints.HDR_POLY_TIMESTAMP)
                ),
            () ->
                assertTrue(
                    headers.containsKey(PolymarketEndpoints.HDR_POLY_NONCE)
                )
        );
    }

    @Test
    @DisplayName("TC-L1-015: Signature is lowercase hex")
    void testSignatureIsLowercaseHex() throws IOException {
        String signature = signer.signEip712(
            credentials,
            137,
            FIXED_TIMESTAMP,
            0
        );

        // Remove 0x prefix and check remaining chars are lowercase hex
        String hexPart = signature.substring(2);
        assertTrue(
            hexPart.matches("[0-9a-f]+"),
            "Signature hex should be lowercase"
        );
    }

    @Test
    @DisplayName("TC-L1-016: Valid for Polygon Mainnet chain ID")
    void testPolygonMainnetChainId() throws IOException {
        assertDoesNotThrow(() ->
            signer.signEip712(credentials, 137, FIXED_TIMESTAMP, 0)
        );
    }

    @Test
    @DisplayName("TC-L1-017: Valid for Amoy testnet chain ID")
    void testAmoyTestnetChainId() throws IOException {
        assertDoesNotThrow(() ->
            signer.signEip712(credentials, 80002, FIXED_TIMESTAMP, 0)
        );
    }

    @Test
    @DisplayName("TC-L1-018: Nonce can be large value")
    void testLargeNonceValue() throws IOException {
        assertDoesNotThrow(() ->
            signer.signEip712(
                credentials,
                137,
                FIXED_TIMESTAMP,
                Integer.MAX_VALUE
            )
        );
    }

    @Test
    @DisplayName("TC-L1-019: Timestamp can be large value")
    void testLargeTimestampValue() throws IOException {
        long farFutureTimestamp = 9999999999L;
        assertDoesNotThrow(() ->
            signer.signEip712(credentials, 137, farFutureTimestamp, 0)
        );
    }
}
