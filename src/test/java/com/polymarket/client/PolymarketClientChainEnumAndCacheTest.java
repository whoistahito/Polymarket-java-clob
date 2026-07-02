package com.polymarket.client;

import com.polymarket.model.Chain;
import com.polymarket.model.OrderBookSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolymarketClient Phase 4 additions:
 *  - Builder.chainId(Chain) overload
 *  - clearTickSizeCache() / clearTickSizeCache(String)
 *  - getOrderBookHash(OrderBookSummary)
 */
@DisplayName("TC-PC4 — PolymarketClient chain enum overload and cache tests")
class PolymarketClientChainEnumAndCacheTest {

    private static final String TEST_PRIVATE_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String FUNDER = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    private PolymarketClient buildClient(int chainId) {
        return new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .funderAddress(FUNDER)
                .chainId(chainId)
                .build();
    }

    @Test
    @DisplayName("TC-PC4-001: Builder.chainId(Chain.POLYGON) resolves to chain id 137")
    void chainEnumOverload() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .funderAddress(FUNDER)
                .chainId(Chain.POLYGON)
                .build();
        assertEquals(137, client.getChainId());
    }

    @Test
    @DisplayName("TC-PC4-002: Builder.chainId(Chain.AMOY) resolves to chain id 80002")
    void chainEnumOverloadAmoy() {
        PolymarketClient client = new PolymarketClient.Builder()
                .privateKey(TEST_PRIVATE_KEY)
                .funderAddress(FUNDER)
                .chainId(Chain.AMOY)
                .build();
        assertEquals(80002, client.getChainId());
    }

    @Test
    @DisplayName("TC-PC4-003: clearTickSizeCache() does not throw")
    void clearTickSizeCache() {
        PolymarketClient client = buildClient(137);
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) client::clearTickSizeCache);
    }

    @Test
    @DisplayName("TC-PC4-004: clearTickSizeCache(String) with unknown token does not throw")
    void clearTickSizeCacheForToken() {
        PolymarketClient client = buildClient(137);
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) () ->
                client.clearTickSizeCache("some-unknown-token"));
    }

    @Test
    @DisplayName("TC-PC4-005: getOrderBookHash returns non-null SHA-1 hex string")
    void getOrderBookHash() {
        PolymarketClient client = buildClient(137);
        OrderBookSummary book = OrderBookSummary.builder()
                .market("0xmarket")
                .assetId("0xasset")
                .hash("old-hash")
                .bids(Collections.emptyList())
                .asks(Collections.emptyList())
                .build();
        String hash = client.getOrderBookHash(book);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        // SHA-1 produces 40 hex chars
        assertEquals(40, hash.length());
    }
}
