package com.polymarket.client;

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

    @Test
    @DisplayName("TC-PC2-001: Builder accepts geoBlockToken")
    void testBuilderGeoBlockToken() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .geoBlockToken("test-token-abc")
                .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("TC-PC2-002: Builder accepts maxRetries")
    void testBuilderMaxRetries() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .maxRetries(3)
                .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("TC-PC2-003: Builder with proxy and maxRetries creates successfully")
    void testBuilderProxyAndRetries() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .proxy("proxy.example.com", 8080)
                .maxRetries(2)
                .build();

        assertNotNull(client);
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
