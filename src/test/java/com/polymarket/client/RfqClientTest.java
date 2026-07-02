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
    // Model builder sanity checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-RFQ-003: RfqUserOrder builder sets all fields correctly")
    void testRfqUserOrderBuilder() {
        RfqUserOrder order = RfqUserOrder.builder()
                .tokenID("abc-token")
                .side(Side.BUY)
                .price(0.65)
                .size(100.0)
                .build();

        assertEquals("abc-token", order.getTokenID());
        assertEquals(Side.BUY, order.getSide());
        assertEquals(0.65, order.getPrice(), 1e-9);
        assertEquals(100.0, order.getSize(), 1e-9);
    }

    @Test
    @DisplayName("TC-RFQ-004: RfqUserQuote builder sets all fields correctly")
    void testRfqUserQuoteBuilder() {
        RfqUserQuote quote = RfqUserQuote.builder()
                .requestId("req-1")
                .price(0.45)
                .side(Side.SELL)
                .size(50.0)
                .build();

        assertEquals("req-1", quote.getRequestId());
        assertEquals(Side.SELL, quote.getSide());
        assertEquals(0.45, quote.getPrice(), 1e-9);
        assertEquals(50.0, quote.getSize(), 1e-9);
    }

    @Test
    @DisplayName("TC-RFQ-005: CreateRfqRequestParams builder sets all fields")
    void testCreateRfqRequestParamsBuilder() {
        CreateRfqRequestParams params = CreateRfqRequestParams.builder()
                .assetIn("tok-1")
                .assetOut("tok-2")
                .amountIn("65000000")
                .amountOut("100000000")
                .userType(1)
                .build();

        assertEquals("tok-1", params.getAssetIn());
        assertEquals("tok-2", params.getAssetOut());
        assertEquals("65000000", params.getAmountIn());
        assertEquals(1, params.getUserType());
    }

    @Test
    @DisplayName("TC-RFQ-006: AcceptQuoteParams builder sets all fields")
    void testAcceptQuoteParamsBuilder() {
        AcceptQuoteParams params = AcceptQuoteParams.builder()
                .requestId("req-abc")
                .quoteId("quote-xyz")
                .expiration(1800000000L)
                .build();

        assertEquals("req-abc", params.getRequestId());
        assertEquals("quote-xyz", params.getQuoteId());
        assertEquals(1800000000L, params.getExpiration());
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

    @Test
    @DisplayName("TC-RFQ-008: GetRfqRequestsParams builder works")
    void testGetRfqRequestsParamsBuilder() {
        GetRfqRequestsParams params = GetRfqRequestsParams.builder()
                .offset("0")
                .limit(10)
                .state("open")
                .build();

        assertEquals("0", params.getOffset());
        assertEquals(10, params.getLimit());
        assertEquals("open", params.getState());
    }
}
