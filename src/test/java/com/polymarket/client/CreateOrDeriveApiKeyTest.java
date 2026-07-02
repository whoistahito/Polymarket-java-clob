package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PMK-012: createOrDeriveApiKey falls back to derive only on an HTTP status error. */
@DisplayName("TC-COD — createOrDeriveApiKey fallback semantics")
class CreateOrDeriveApiKeyTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String CREDS_JSON =
        "{\"apiKey\":\"k\",\"secret\":\"s\",\"passphrase\":\"p\"}";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PolymarketClient.Builder().privateKey(PK).clobHost(server.url("/").toString()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("TC-COD-001 status error on create falls back to derive")
    void statusErrorFallsBack() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{}"));
        server.enqueue(new MockResponse().setBody(CREDS_JSON).addHeader("Content-Type", "application/json"));

        ApiKeyCreds creds = client.createOrDeriveApiKey();

        assertEquals("k", creds.getKey());
        assertEquals(2, server.getRequestCount()); // create (400) + derive
    }

    @Test
    @DisplayName("TC-COD-002 non-status IOException (parse) propagates; derive not called")
    void parseErrorPropagates() {
        // 200 OK but the body is missing required fields -> parse IOException, not a status error.
        server.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        assertThrows(IOException.class, () -> client.createOrDeriveApiKey());
        assertEquals(1, server.getRequestCount()); // only the create attempt; no derive fallback
    }

    @Test
    @DisplayName("TC-COD-003 successful create returns those creds; derive not called")
    void successNoDerive() throws Exception {
        server.enqueue(new MockResponse().setBody(CREDS_JSON).addHeader("Content-Type", "application/json"));

        ApiKeyCreds creds = client.createOrDeriveApiKey();

        assertEquals("k", creds.getKey());
        assertEquals(1, server.getRequestCount()); // only create
    }
}
