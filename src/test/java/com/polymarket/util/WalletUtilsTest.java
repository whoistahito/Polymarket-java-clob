package com.polymarket.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test vectors are sourced from Rust rs-clob-client/src/lib.rs test module.
 * EOA: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
 * Expected proxy (Polygon): 0x365f0cA36ae1F641E02Fe3b7743673DA42A13a70
 * Expected safe (Polygon + Amoy): 0xd93b25Cb943D14d0d34FBAf01fc93a0F8b5f6e47
 */
@DisplayName("WalletUtils CREATE2 derivation tests")
class WalletUtilsTest {

    private static final String TEST_EOA = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    private static final String EXPECTED_PROXY_POLYGON =
            "0x365f0ca36ae1f641e02fe3b7743673da42a13a70";

    private static final String EXPECTED_SAFE =
            "0xd93b25cb943d14d0d34fbaf01fc93a0f8b5f6e47";

    @Test
    @DisplayName("TC-WU-001: deriveProxyWallet on Polygon (137) matches test vector")
    void proxyPolygon() {
        Optional<String> result = WalletUtils.deriveProxyWallet(TEST_EOA, 137);
        assertTrue(result.isPresent(), "Should return a proxy address for chain 137");
        assertEquals(EXPECTED_PROXY_POLYGON, result.get().toLowerCase());
    }

    @Test
    @DisplayName("TC-WU-002: deriveProxyWallet on Amoy (80002) returns empty")
    void proxyAmoyEmpty() {
        Optional<String> result = WalletUtils.deriveProxyWallet(TEST_EOA, 80002);
        assertFalse(result.isPresent(), "Proxy not supported on Amoy");
    }

    @Test
    @DisplayName("TC-WU-003: deriveProxyWallet on unsupported chain returns empty")
    void proxyUnsupportedChain() {
        Optional<String> result = WalletUtils.deriveProxyWallet(TEST_EOA, 1);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("TC-WU-004: deriveSafeWallet on Polygon (137) matches test vector")
    void safePolygon() {
        Optional<String> result = WalletUtils.deriveSafeWallet(TEST_EOA, 137);
        assertTrue(result.isPresent());
        assertEquals(EXPECTED_SAFE, result.get().toLowerCase());
    }

    @Test
    @DisplayName("TC-WU-005: deriveSafeWallet on Amoy (80002) matches test vector")
    void safeAmoy() {
        Optional<String> result = WalletUtils.deriveSafeWallet(TEST_EOA, 80002);
        assertTrue(result.isPresent(), "Safe supported on Amoy");
        assertEquals(EXPECTED_SAFE, result.get().toLowerCase());
    }

    @Test
    @DisplayName("TC-WU-006: deriveSafeWallet on unsupported chain returns empty")
    void safeUnsupportedChain() {
        Optional<String> result = WalletUtils.deriveSafeWallet(TEST_EOA, 1);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("TC-WU-007: Address without 0x prefix is accepted")
    void addressWithoutPrefix() {
        String eoaNoPrefix = TEST_EOA.substring(2);
        Optional<String> proxy = WalletUtils.deriveProxyWallet(eoaNoPrefix, 137);
        Optional<String> safe = WalletUtils.deriveSafeWallet(eoaNoPrefix, 137);
        assertTrue(proxy.isPresent());
        assertTrue(safe.isPresent());
        assertEquals(EXPECTED_PROXY_POLYGON, proxy.get().toLowerCase());
        assertEquals(EXPECTED_SAFE, safe.get().toLowerCase());
    }
}
