package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

/**
 * Test cases for PolymarketClient.
 * Verifies client builder, configuration, and basic functionality.
 */
@DisplayName("PolymarketClient Tests")
class PolymarketClientTest {

    private static final String TEST_PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String TEST_PRIVATE_KEY_WITH_PREFIX = "0x" + TEST_PRIVATE_KEY;
    private static final String TEST_FUNDER_ADDRESS = "0x1234567890123456789012345678901234567890";

    private Credentials credentials;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(TEST_PRIVATE_KEY);
    }

    @Test
    @DisplayName("TC-PC-001: Builder creates client with private key")
    void testBuilderCreatesClient() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .chainId(137)
                .build();

        assertNotNull(client);
        assertEquals(137, client.getChainId());
        assertFalse(client.hasApiCreds());
    }

    @Test
    @DisplayName("TC-PC-001b: Builder creates client with private key without 0x prefix")
    void testBuilderCreatesClientWithoutPrefix() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .chainId(137)
                .build();

        assertNotNull(client);
        assertEquals(137, client.getChainId());
    }

    @Test
    @DisplayName("TC-PC-001c: Builder creates client with Credentials object")
    void testBuilderCreatesClientWithCredentials() {
        PolymarketClient client = new PolymarketClient.Builder()
                .credentials(credentials)
                .chainId(137)
                .build();

        assertNotNull(client);
        assertEquals(credentials.getAddress(), client.getAddress());
    }

    @Test
    @DisplayName("TC-PC-002: Builder requires private key or credentials")
    void testBuilderRequiresPrivateKey() {
        assertThrows(NullPointerException.class, () -> {
            new PolymarketClient.Builder()
                    .chainId(137)
                    .build();
        });
    }

    @Test
    @DisplayName("TC-PC-003: Builder with API credentials")
    void testBuilderWithApiCredentials() {
        ApiKeyCreds creds = new ApiKeyCreds("key", "secret", "pass");

        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .apiCreds(creds)
                .build();

        assertTrue(client.hasApiCreds());
        assertEquals(creds, client.getApiCreds());
    }

    @Test
    @DisplayName("TC-PC-004: L2 auth required for trading operations")
    void testL2AuthRequiredForTrading() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .build();

        // No API creds, should fail with IllegalStateException
        assertThrows(IllegalStateException.class, () -> client.getOpenOrders());
        assertThrows(IllegalStateException.class, () -> client.cancelAll());
        assertThrows(IllegalStateException.class, () -> client.getApiKeys());
    }

    @Test
    @DisplayName("TC-PC-005: Host URL trailing slash is stripped, avoiding a double slash in requests")
    void testHostUrlStripping() throws Exception {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(200).setBody("1700000000"));

            String hostWithTrailingSlash = server.url("/").toString();
            PolymarketClient client = new PolymarketClient.Builder()
                    .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                    .clobHost(hostWithTrailingSlash)
                    .build();

            client.getServerTime();

            RecordedRequest request = server.takeRequest();
            assertEquals("/time", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    @DisplayName("TC-PC-006: Funder address support")
    void testFunderAddressSupport() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .funderAddress(TEST_FUNDER_ADDRESS)
                .build();

        assertEquals(TEST_FUNDER_ADDRESS, client.getFunderAddress());
    }

    @Test
    @DisplayName("TC-PC-007: Default chain ID is 137 (Polygon Mainnet)")
    void testDefaultChainId() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .build();

        assertEquals(137, client.getChainId());
    }

    @Test
    @DisplayName("TC-PC-008: Amoy testnet chain ID is supported")
    void testAmoyChainId() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .chainId(80002)
                .build();

        assertEquals(80002, client.getChainId());
    }

    @Test
    @DisplayName("TC-PC-009: getAddress returns correct address")
    void testGetAddressReturnsCorrectAddress() {
        PolymarketClient client = new PolymarketClient.Builder()
                .credentials(credentials)
                .build();

        assertEquals(credentials.getAddress(), client.getAddress());
    }

    @Test
    @DisplayName("TC-PC-010: hasApiCreds returns false when no API creds")
    void testHasApiCredsReturnsFalseWhenNoApiCreds() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .build();

        assertFalse(client.hasApiCreds());
    }

    @Test
    @DisplayName("TC-PC-011: hasApiCreds returns true when API creds present")
    void testHasApiCredsReturnsTrueWhenApiCredsPresent() {
        ApiKeyCreds creds = new ApiKeyCreds("key", "secret", "pass");

        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .apiCreds(creds)
                .build();

        assertTrue(client.hasApiCreds());
    }

    @Test
    @DisplayName("TC-PC-012: getApiCreds returns null when no API creds")
    void testGetApiCredsReturnsNullWhenNoApiCreds() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .build();

        assertNull(client.getApiCreds());
    }

    @Test
    @DisplayName("TC-PC-013: getFunderAddress returns null when not set")
    void testGetFunderAddressReturnsNullWhenNotSet() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .build();

        assertNull(client.getFunderAddress());
    }

    @Test
    @DisplayName("TC-PC-015: Builder chain methods return builder for fluent API")
    void testBuilderChainMethods() {
        PolymarketClient.Builder builder = new PolymarketClient.Builder();

        assertSame(builder, builder.privateKey(TEST_PRIVATE_KEY_WITH_PREFIX));
        assertSame(builder, builder.chainId(137));
        assertSame(builder, builder.clobHost("https://test.com"));
        assertSame(builder, builder.gammaHost("https://gamma.test.com"));
        assertSame(builder, builder.funderAddress(TEST_FUNDER_ADDRESS));
        assertSame(builder, builder.apiCreds(new ApiKeyCreds("k", "s", "p")));
        assertSame(builder, builder.httpClient(new HttpClient()));
    }

    @Test
    @DisplayName("TC-PC-016: Client is immutable after building")
    void testClientImmutability() {
        ApiKeyCreds creds = new ApiKeyCreds("key", "secret", "pass");

        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .chainId(137)
                .funderAddress(TEST_FUNDER_ADDRESS)
                .apiCreds(creds)
                .build();

        // Multiple calls should return same values
        assertEquals(137, client.getChainId());
        assertEquals(137, client.getChainId());
        assertEquals(TEST_FUNDER_ADDRESS, client.getFunderAddress());
        assertEquals(TEST_FUNDER_ADDRESS, client.getFunderAddress());
        assertSame(creds, client.getApiCreds());
        assertSame(creds, client.getApiCreds());
    }

    @Test
    @DisplayName("TC-PC-017: Address format is correct Ethereum address")
    void testAddressFormatIsCorrect() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .build();

        String address = client.getAddress();

        assertTrue(address.startsWith("0x"), "Address should start with 0x");
        assertEquals(42, address.length(), "Address should be 42 chars (0x + 40 hex chars)");
        assertTrue(address.substring(2).matches("[0-9a-fA-F]+"), "Address should be valid hex");
    }

    @Test
    @DisplayName("TC-PC-018: Multiple clients can be built independently")
    void testMultipleClientsCanBeBuilt() {
        PolymarketClient client1 = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .chainId(137)
                .build();

        String otherPrivateKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        PolymarketClient client2 = new PolymarketClient.Builder()
                .privateKey(otherPrivateKey)
                .chainId(80002)
                .build();

        assertNotEquals(client1.getAddress(), client2.getAddress());
        assertNotEquals(client1.getChainId(), client2.getChainId());
    }

    @Test
    @DisplayName("TC-PC-019: Builder can be reused (though not recommended)")
    void testBuilderReuse() {
        PolymarketClient.Builder builder = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
                .chainId(137);

        PolymarketClient client1 = builder.build();
        PolymarketClient client2 = builder.chainId(80002).build();

        assertEquals(137, client1.getChainId());
        assertEquals(80002, client2.getChainId());
    }

  @Test
  @DisplayName("TC-PC-021: L2 headers use signer address when funder is configured")
  void testL2HeadersUseSignerAddressWhenFunderIsSet() {
    ApiKeyCreds creds = new ApiKeyCreds("key", "secret", "pass");

    PolymarketClient client =
        new PolymarketClient.Builder()
            .privateKey(TEST_PRIVATE_KEY_WITH_PREFIX)
            .funderAddress(TEST_FUNDER_ADDRESS)
            .apiCreds(creds)
            .build();

    assertEquals(TEST_FUNDER_ADDRESS, client.getAddress());

    String polyAddress = client.l2Headers("GET", "/balance-allowance", null).get("POLY_ADDRESS");
    assertEquals(credentials.getAddress(), polyAddress);
  }
}
