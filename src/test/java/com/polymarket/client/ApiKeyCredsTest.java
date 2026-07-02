package com.polymarket.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for ApiKeyCreds.
 * Verifies API credentials handling matches TypeScript SDK interface.
 */
@DisplayName("ApiKeyCreds Tests")
class ApiKeyCredsTest {

    private static final String TEST_KEY = "my-api-key";
    private static final String TEST_SECRET = "my-secret-key";
    private static final String TEST_PASSPHRASE = "my-passphrase";

    @Test
    @DisplayName("TC-AK-001: Constructor and getters work correctly")
    void testConstructorAndGetters() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        assertEquals(TEST_KEY, creds.getKey());
        assertEquals(TEST_SECRET, creds.getSecret());
        assertEquals(TEST_PASSPHRASE, creds.getPassphrase());
    }

    @Test
    @DisplayName("TC-AK-002a: Null key validation throws NullPointerException")
    void testNullKeyValidation() {
        assertThrows(NullPointerException.class,
                () -> new ApiKeyCreds(null, TEST_SECRET, TEST_PASSPHRASE));
    }

    @Test
    @DisplayName("TC-AK-002b: Null secret validation throws NullPointerException")
    void testNullSecretValidation() {
        assertThrows(NullPointerException.class,
                () -> new ApiKeyCreds(TEST_KEY, null, TEST_PASSPHRASE));
    }

    @Test
    @DisplayName("TC-AK-002c: Null passphrase validation throws NullPointerException")
    void testNullPassphraseValidation() {
        assertThrows(NullPointerException.class,
                () -> new ApiKeyCreds(TEST_KEY, TEST_SECRET, null));
    }

    @Test
    @DisplayName("TC-AK-003: ToString hides secret for security")
    void testToStringHidesSecret() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        String str = creds.toString();

        assertTrue(str.contains(TEST_KEY), "toString should contain key");
        assertTrue(str.contains(TEST_PASSPHRASE), "toString should contain passphrase");
        assertFalse(str.contains(TEST_SECRET), "toString should NOT contain secret");
        assertTrue(str.contains("***"), "toString should contain masked secret indicator");
    }

    @Test
    @DisplayName("TC-AK-004a: Equals returns true for identical credentials")
    void testEqualsIdenticalCredentials() {
        ApiKeyCreds creds1 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);
        ApiKeyCreds creds2 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        assertEquals(creds1, creds2);
    }

    @Test
    @DisplayName("TC-AK-004b: HashCode is same for identical credentials")
    void testHashCodeIdenticalCredentials() {
        ApiKeyCreds creds1 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);
        ApiKeyCreds creds2 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        assertEquals(creds1.hashCode(), creds2.hashCode());
    }

    @Test
    @DisplayName("TC-AK-004c: Equals returns false for different key")
    void testEqualsDifferentKey() {
        ApiKeyCreds creds1 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);
        ApiKeyCreds creds2 = new ApiKeyCreds("different-key", TEST_SECRET, TEST_PASSPHRASE);

        assertNotEquals(creds1, creds2);
    }

    @Test
    @DisplayName("TC-AK-004d: Equals returns false for different secret")
    void testEqualsDifferentSecret() {
        ApiKeyCreds creds1 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);
        ApiKeyCreds creds2 = new ApiKeyCreds(TEST_KEY, "different-secret", TEST_PASSPHRASE);

        assertNotEquals(creds1, creds2);
    }

    @Test
    @DisplayName("TC-AK-004e: Equals returns false for different passphrase")
    void testEqualsDifferentPassphrase() {
        ApiKeyCreds creds1 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);
        ApiKeyCreds creds2 = new ApiKeyCreds(TEST_KEY, TEST_SECRET, "different-passphrase");

        assertNotEquals(creds1, creds2);
    }

    @Test
    @DisplayName("TC-AK-005: Equals returns true for same instance")
    void testEqualsSameInstance() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        assertEquals(creds, creds);
    }

    @Test
    @DisplayName("TC-AK-006: Equals returns false for null")
    void testEqualsNull() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        assertNotEquals(null, creds);
    }

    @Test
    @DisplayName("TC-AK-007: Equals returns false for different class")
    void testEqualsDifferentClass() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        assertNotEquals("not an ApiKeyCreds", creds);
    }

    @Test
    @DisplayName("TC-AK-008: Empty strings are allowed")
    void testEmptyStringsAllowed() {
        // Empty strings should be allowed (null is rejected, but empty is valid)
        ApiKeyCreds creds = new ApiKeyCreds("", "", "");

        assertEquals("", creds.getKey());
        assertEquals("", creds.getSecret());
        assertEquals("", creds.getPassphrase());
    }

    @Test
    @DisplayName("TC-AK-009: Special characters in credentials")
    void testSpecialCharactersInCredentials() {
        String keyWithSpecialChars = "key-with-special_chars.123";
        String secretWithSpecialChars = "secret+with/special=chars==";
        String passphraseWithSpecialChars = "pass phrase with spaces!@#$%";

        ApiKeyCreds creds = new ApiKeyCreds(
                keyWithSpecialChars, secretWithSpecialChars, passphraseWithSpecialChars
        );

        assertEquals(keyWithSpecialChars, creds.getKey());
        assertEquals(secretWithSpecialChars, creds.getSecret());
        assertEquals(passphraseWithSpecialChars, creds.getPassphrase());
    }

    @Test
    @DisplayName("TC-AK-010: Long credential strings")
    void testLongCredentialStrings() {
        String longKey = "k".repeat(1000);
        String longSecret = "s".repeat(1000);
        String longPassphrase = "p".repeat(1000);

        ApiKeyCreds creds = new ApiKeyCreds(longKey, longSecret, longPassphrase);

        assertEquals(longKey, creds.getKey());
        assertEquals(longSecret, creds.getSecret());
        assertEquals(longPassphrase, creds.getPassphrase());
    }

    @Test
    @DisplayName("TC-AK-011: Unicode characters in credentials")
    void testUnicodeCharactersInCredentials() {
        String unicodeKey = "键-🔑";
        String unicodeSecret = "秘密-🔒";
        String unicodePassphrase = "密码-🔐";

        ApiKeyCreds creds = new ApiKeyCreds(unicodeKey, unicodeSecret, unicodePassphrase);

        assertEquals(unicodeKey, creds.getKey());
        assertEquals(unicodeSecret, creds.getSecret());
        assertEquals(unicodePassphrase, creds.getPassphrase());
    }

    @Test
    @DisplayName("TC-AK-012: HashCode consistency across multiple calls")
    void testHashCodeConsistency() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        int hashCode1 = creds.hashCode();
        int hashCode2 = creds.hashCode();
        int hashCode3 = creds.hashCode();

        assertEquals(hashCode1, hashCode2);
        assertEquals(hashCode2, hashCode3);
    }

    @Test
    @DisplayName("TC-AK-013: Different credentials have different hash codes")
    void testDifferentCredentialsHaveDifferentHashCodes() {
        ApiKeyCreds creds1 = new ApiKeyCreds("key1", "secret1", "pass1");
        ApiKeyCreds creds2 = new ApiKeyCreds("key2", "secret2", "pass2");

        // Note: Different objects CAN have same hash code (collision),
        // but these are different enough that they shouldn't
        assertNotEquals(creds1.hashCode(), creds2.hashCode());
    }

    @Test
    @DisplayName("TC-AK-014: Immutability - getters always return same values")
    void testImmutability() {
        ApiKeyCreds creds = new ApiKeyCreds(TEST_KEY, TEST_SECRET, TEST_PASSPHRASE);

        String key1 = creds.getKey();
        String key2 = creds.getKey();
        String secret1 = creds.getSecret();
        String secret2 = creds.getSecret();
        String pass1 = creds.getPassphrase();
        String pass2 = creds.getPassphrase();

        assertSame(key1, key2);
        assertSame(secret1, secret2);
        assertSame(pass1, pass2);
    }
}
