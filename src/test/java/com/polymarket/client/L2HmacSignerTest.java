package com.polymarket.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for L2HmacSigner.
 * Verifies HMAC-SHA256 signature generation for L2 authentication matches TypeScript SDK.
 */
@DisplayName("L2HmacSigner Tests")
class L2HmacSignerTest {

    private static final long FIXED_TIMESTAMP = 1700000000L;
    // URL-safe Base64 encoded secret (32 bytes)
    private static final String TEST_SECRET = "SGVsbG9Xb3JsZFRoaXNJc0FUZXN0U2VjcmV0S2V5IQ";

    private Clock fixedClock;
    private L2HmacSigner signer;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.ofEpochSecond(FIXED_TIMESTAMP), ZoneId.of("UTC"));
        signer = new L2HmacSigner(fixedClock);
    }

    @Test
    @DisplayName("TC-L2-001: Build message format matches TypeScript SDK")
    void testBuildMessageFormat() {
        // TypeScript: timestamp + method + requestPath + body
        String message = L2HmacSigner.buildMessage(FIXED_TIMESTAMP, "POST", "/order", "{\"test\":\"data\"}");

        assertEquals("1700000000POST/order{\"test\":\"data\"}", message);
    }

    @Test
    @DisplayName("TC-L2-002: Build message without body")
    void testBuildMessageWithoutBody() {
        String message = L2HmacSigner.buildMessage(FIXED_TIMESTAMP, "GET", "/markets", null);

        assertEquals("1700000000GET/markets", message);
    }

    @Test
    @DisplayName("TC-L2-003: Build message with empty body")
    void testBuildMessageWithEmptyBody() {
        String message = L2HmacSigner.buildMessage(FIXED_TIMESTAMP, "GET", "/markets", "");

        assertEquals("1700000000GET/markets", message);
    }

    @Test
    @DisplayName("TC-L2-004: Quote replacement for Python compatibility")
    void testQuoteReplacement() {
        // Java implementation includes single quote to double quote replacement for Python compatibility
        String message = L2HmacSigner.buildMessage(FIXED_TIMESTAMP, "POST", "/order", "{'key':'value'}");

        assertEquals("1700000000POST/order{\"key\":\"value\"}", message);
    }

    @Test
    @DisplayName("TC-L2-005: Signature with known secret produces valid URL-safe Base64")
    void testSignWithKnownSecret() {
        String signature = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/markets", null);

        assertNotNull(signature);
        // URL-safe Base64 uses - and _ instead of + and /
        assertTrue(signature.matches("^[A-Za-z0-9_-]+=*$"), "Should be URL-safe Base64");
    }

    @Test
    @DisplayName("TC-L2-006: SignNow returns correct payload with timestamp and signature")
    void testSignNowReturnsPayload() {
        L2HmacSigner.SignedL2Payload payload = signer.signNow(TEST_SECRET, "POST", "/order", "{\"test\":true}");

        assertEquals(FIXED_TIMESTAMP, payload.timestampSeconds());
        assertNotNull(payload.signature());
        assertFalse(payload.signature().isEmpty());
    }

    @Test
    @DisplayName("TC-L2-007: Base64 padding normalization")
    void testBase64PaddingNormalization() {
        // Secret without padding
        String secretNoPadding = "SGVsbG9Xb3JsZA";
        // Secret with padding
        String secretWithPadding = "SGVsbG9Xb3JsZA==";

        String sig1 = signer.sign(secretNoPadding, FIXED_TIMESTAMP, "GET", "/test", null);
        String sig2 = signer.sign(secretWithPadding, FIXED_TIMESTAMP, "GET", "/test", null);

        assertEquals(sig1, sig2, "Both should produce same signature");
    }

    @Test
    @DisplayName("TC-L2-008: URL-safe Base64 secret decoding with special chars")
    void testUrlSafeBase64SecretDecoding() {
        // URL-safe Base64 with - and _
        String urlSafeSecret = "SGVsbG8td29ybGRfMTIz";

        assertDoesNotThrow(() -> signer.sign(urlSafeSecret, FIXED_TIMESTAMP, "GET", "/test", null));
    }

    @Test
    @DisplayName("TC-L2-009: Different methods produce different signatures")
    void testDifferentMethodsProduceDifferentSignatures() {
        String sigGet = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/order", null);
        String sigPost = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "POST", "/order", null);

        assertNotEquals(sigGet, sigPost, "Different methods should produce different signatures");
    }

    @Test
    @DisplayName("TC-L2-010: Different paths produce different signatures")
    void testDifferentPathsProduceDifferentSignatures() {
        String sig1 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/markets", null);
        String sig2 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/orders", null);

        assertNotEquals(sig1, sig2, "Different paths should produce different signatures");
    }

    @Test
    @DisplayName("TC-L2-011: Different timestamps produce different signatures")
    void testDifferentTimestampsProduceDifferentSignatures() {
        String sig1 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/markets", null);
        String sig2 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP + 1, "GET", "/markets", null);

        assertNotEquals(sig1, sig2, "Different timestamps should produce different signatures");
    }

    @Test
    @DisplayName("TC-L2-012: Different bodies produce different signatures")
    void testDifferentBodiesProduceDifferentSignatures() {
        String sig1 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "POST", "/order", "{\"side\":\"BUY\"}");
        String sig2 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "POST", "/order", "{\"side\":\"SELL\"}");

        assertNotEquals(sig1, sig2, "Different bodies should produce different signatures");
    }

    @Test
    @DisplayName("TC-L2-013: Same inputs produce same signature (deterministic)")
    void testDeterministicSignature() {
        String sig1 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/markets", null);
        String sig2 = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/markets", null);

        assertEquals(sig1, sig2, "Same inputs should produce same signature");
    }

    @Test
    @DisplayName("TC-L2-014: Constructor with default clock")
    void testDefaultClockConstructor() {
        L2HmacSigner defaultSigner = new L2HmacSigner();
        assertNotNull(defaultSigner);
    }

    @Test
    @DisplayName("TC-L2-015: Constructor rejects null clock")
    void testNullClockRejected() {
        assertThrows(NullPointerException.class, () -> new L2HmacSigner(null));
    }

    @Test
    @DisplayName("TC-L2-016: BuildMessage rejects null method")
    void testBuildMessageRejectsNullMethod() {
        assertThrows(NullPointerException.class,
                () -> L2HmacSigner.buildMessage(FIXED_TIMESTAMP, null, "/test", null));
    }

    @Test
    @DisplayName("TC-L2-017: BuildMessage rejects null request path")
    void testBuildMessageRejectsNullRequestPath() {
        assertThrows(NullPointerException.class,
                () -> L2HmacSigner.buildMessage(FIXED_TIMESTAMP, "GET", null, null));
    }

    @Test
    @DisplayName("TC-L2-018: Sign rejects null secret")
    void testSignRejectsNullSecret() {
        assertThrows(NullPointerException.class,
                () -> signer.sign(null, FIXED_TIMESTAMP, "GET", "/test", null));
    }

    @Test
    @DisplayName("TC-L2-019: Sign rejects null method")
    void testSignRejectsNullMethod() {
        assertThrows(NullPointerException.class,
                () -> signer.sign(TEST_SECRET, FIXED_TIMESTAMP, null, "/test", null));
    }

    @Test
    @DisplayName("TC-L2-020: Sign rejects null request path")
    void testSignRejectsNullRequestPath() {
        assertThrows(NullPointerException.class,
                () -> signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", null, null));
    }

    @Test
    @DisplayName("TC-L2-021: SignedL2Payload record contains correct values")
    void testSignedL2PayloadRecord() {
        L2HmacSigner.SignedL2Payload payload = new L2HmacSigner.SignedL2Payload(FIXED_TIMESTAMP, "test-signature");

        assertEquals(FIXED_TIMESTAMP, payload.timestampSeconds());
        assertEquals("test-signature", payload.signature());
    }

    @Test
    @DisplayName("TC-L2-022: DELETE method works correctly")
    void testDeleteMethod() {
        String signature = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "DELETE", "/order", "{\"orderID\":\"123\"}");

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("TC-L2-023: Complex JSON body handled correctly")
    void testComplexJsonBody() {
        String complexBody = "{\"order\":{\"tokenId\":\"12345\",\"side\":\"BUY\",\"price\":\"0.50\"},\"orderType\":\"GTC\"}";

        String signature = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "POST", "/order", complexBody);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("TC-L2-024: Long request path handled correctly")
    void testLongRequestPath() {
        String longPath = "/api/v1/orders/very/long/nested/path/with/multiple/segments";

        String signature = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", longPath, null);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("TC-L2-025: Signature does not contain standard Base64 chars + or /")
    void testSignatureIsUrlSafe() {
        // Generate several signatures to increase chance of catching non-URL-safe chars
        for (int i = 0; i < 10; i++) {
            String signature = signer.sign(TEST_SECRET, FIXED_TIMESTAMP + i, "POST", "/order", "{\"i\":" + i + "}");

            assertFalse(signature.contains("+"), "Signature should not contain +");
            assertFalse(signature.contains("/"), "Signature should not contain /");
        }
    }

    @Test
    @DisplayName("TC-L2-026: Empty string body treated as no body")
    void testEmptyStringBodyTreatedAsNoBody() {
        String sigWithNull = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/test", null);
        String sigWithEmpty = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/test", "");

        assertEquals(sigWithNull, sigWithEmpty, "Empty string body should be treated as no body");
    }

    @Test
    @DisplayName("TC-L2-027: Whitespace-only body included in signature")
    void testWhitespaceBodyIncluded() {
        String sigWithNull = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/test", null);
        String sigWithWhitespace = signer.sign(TEST_SECRET, FIXED_TIMESTAMP, "GET", "/test", "   ");

        assertNotEquals(sigWithNull, sigWithWhitespace, "Whitespace body should be included in signature");
    }
}
