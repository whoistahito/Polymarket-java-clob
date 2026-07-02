package com.polymarket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.client.ApiKeyCreds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiKeyRaw model tests")
class ApiKeyRawTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-AKR-002: toApiKeyCreds maps fields correctly")
    void toApiKeyCreds() {
        ApiKeyRaw raw = ApiKeyRaw.builder()
                .apiKey("key-123")
                .secret("sec-abc")
                .passphrase("pass-xyz")
                .build();
        ApiKeyCreds creds = raw.toApiKeyCreds();
        assertEquals("key-123", creds.getKey());
        assertEquals("sec-abc", creds.getSecret());
        assertEquals("pass-xyz", creds.getPassphrase());
    }

    @Test
    @DisplayName("TC-AKR-003: JSON deserialization with camelCase")
    void jsonDeserializeCamelCase() throws Exception {
        String json = "{\"apiKey\":\"k\",\"secret\":\"s\",\"passphrase\":\"p\"}";
        ApiKeyRaw raw = MAPPER.readValue(json, ApiKeyRaw.class);
        assertEquals("k", raw.getApiKey());
        assertEquals("s", raw.getSecret());
        assertEquals("p", raw.getPassphrase());
    }
}
