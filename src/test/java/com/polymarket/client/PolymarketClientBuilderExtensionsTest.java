package com.polymarket.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PolymarketClient builder extensions: geoBlockToken, maxRetries,
 * rfq() sub-client, and wallet address derivation.
 */
@DisplayName("TC-PC-EXT — PolymarketClient builder extensions tests")
class PolymarketClientBuilderExtensionsTest {

    private static final String TEST_PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("TC-PC2-001: geoBlockToken is appended as a query parameter on every request")
    void testBuilderGeoBlockToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("1700000000"));

        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .clobHost(server.url("").toString())
                .geoBlockToken("test-token-abc")
                .build();

        client.getServerTime();

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("geo_block_token=test-token-abc"));
    }

    @Test
    @DisplayName("TC-PC2-002: maxRetries is wired through to the underlying HttpClient")
    void testBuilderMaxRetries() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("1700000000"));

        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .clobHost(server.url("").toString())
                .maxRetries(1)
                .build();

        long time = client.getServerTime();

        assertEquals(1700000000L, time);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PC2-004: rfq() returns non-null RfqClient")
    void testRfqSubClientNotNull() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .build();

        assertNotNull(client.rfq());
    }

    @Test
    @DisplayName("TC-PC2-005: rfq() returns new instance on each call")
    void testRfqSubClientNewInstance() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .build();

        RfqClient rfq1 = client.rfq();
        RfqClient rfq2 = client.rfq();
        assertNotSame(rfq1, rfq2);
    }

    @Test
    @DisplayName("TC-PC2-007: getAddress returns correct address for known key")
    void testGetAddress() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .build();

        // The known address for the Hardhat default private key
        assertEquals(
                "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266".toLowerCase(),
                client.getAddress().toLowerCase()
        );
    }
}
