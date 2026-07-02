package com.polymarket.client;

import com.polymarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RfqClient round-config logic and sub-client creation.
 *
 * <p>Network-calling methods are not tested here (they require live credentials).
 * This class focuses on the deterministic logic that can be exercised offline.
 */
@DisplayName("RfqClient Tests")
class RfqClientTest {

    private static final String TEST_PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private PolymarketClient polymarketClient;

    @BeforeEach
    void setUp() {
        polymarketClient = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .build();
    }

    // -------------------------------------------------------------------------
    // RfqClient instantiation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-RFQ-001: rfq() creates a non-null RfqClient")
    void testRfqClientCreated() {
        RfqClient rfq = polymarketClient.rfq();
        assertNotNull(rfq);
    }

    @Test
    @DisplayName("TC-RFQ-002: requireL2Auth throws when no API creds present")
    void testRequiresL2AuthThrowsWithoutCreds() {
        RfqClient rfq = polymarketClient.rfq();
        // createRfqRequest calls requireL2Auth internally
        assertThrows(IllegalStateException.class, () ->
                rfq.createRfqRequest(
                        RfqUserOrder.builder()
                                .tokenID("123")
                                .side(Side.BUY)
                                .price(0.5)
                                .size(10.0)
                                .build(),
                        "0.01"
                )
        );
    }

    // -------------------------------------------------------------------------
    // Enum checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-RFQ-007: RfqMatchType enum values are correct")
    void testRfqMatchTypeValues() {
        assertEquals("COMPLEMENTARY", RfqMatchType.COMPLEMENTARY.getValue());
        assertEquals("MERGE", RfqMatchType.MERGE.getValue());
        assertEquals("MINT", RfqMatchType.MINT.getValue());
    }
}
