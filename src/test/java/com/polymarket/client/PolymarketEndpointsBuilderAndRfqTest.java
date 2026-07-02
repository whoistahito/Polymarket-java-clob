package com.polymarket.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.polymarket.client.PolymarketEndpoints.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for PolymarketEndpoints builder API key constants and RFQ endpoint constants.
 */
@DisplayName("TC-EP-EXT — PolymarketEndpoints builder API key and RFQ endpoint tests")
class PolymarketEndpointsBuilderAndRfqTest {

    @Test
    @DisplayName("TC-EP2-001: Builder trades endpoint")
    void testBuilderTradesEndpoint() {
        assertEquals("/builder/trades", CLOB_BUILDER_TRADES);
    }

    @Test
    @DisplayName("TC-EP2-002: Builder API key endpoints (same path, different HTTP method)")
    void testBuilderApiKeyEndpoints() {
        assertEquals("/auth/builder-api-key", CLOB_CREATE_BUILDER_API_KEY);
        assertEquals("/auth/builder-api-key", CLOB_GET_BUILDER_API_KEYS);
        assertEquals("/auth/builder-api-key", CLOB_REVOKE_BUILDER_API_KEY);
    }

    @Test
    @DisplayName("TC-EP2-003: RFQ request endpoints")
    void testRfqRequestEndpoints() {
        assertEquals("/rfq/request", RFQ_CREATE_REQUEST);
        assertEquals("/rfq/request", RFQ_CANCEL_REQUEST);
        assertEquals("/rfq/data/requests", RFQ_GET_REQUESTS);
    }

    @Test
    @DisplayName("TC-EP2-004: RFQ quote endpoints")
    void testRfqQuoteEndpoints() {
        assertEquals("/rfq/data/requester/quotes", RFQ_REQUESTER_QUOTES);
        assertEquals("/rfq/data/quoter/quotes", RFQ_QUOTER_QUOTES);
        assertEquals("/rfq/data/best-quote", RFQ_BEST_QUOTE);
        assertEquals("/rfq/quote", RFQ_CREATE_QUOTE);
        assertEquals("/rfq/quote", RFQ_CANCEL_QUOTE);
    }

    @Test
    @DisplayName("TC-EP2-005: RFQ config and accept endpoints")
    void testRfqConfigEndpoints() {
        assertEquals("/rfq/config", RFQ_CONFIG);
        assertEquals("/rfq/request/accept", RFQ_ACCEPT_REQUEST);
        assertEquals("/rfq/quote/approve", RFQ_APPROVE_QUOTE);
    }
}
