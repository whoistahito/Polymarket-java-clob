package com.polymarket.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiKeyCreds Tests")
class ApiKeyCredsTest {

    private static final String TEST_KEY = "my-api-key";
    private static final String TEST_SECRET = "my-secret-key";
    private static final String TEST_PASSPHRASE = "my-passphrase";

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
}
