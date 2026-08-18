package com.polymarket.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CREATE2 wallet derivation, restored from the proven 1.0 {@code WalletUtils} after issue #28's
 * facade deletion dropped it without a 2.0 replacement. Vectors: rs-clob-client/src/lib.rs tests.
 */
@DisplayName("SigningIdentity CREATE2 wallet derivation")
class WalletDerivationTest {

    private static final String EOA = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final String EXPECTED_PROXY = "0x365f0ca36ae1f641e02fe3b7743673da42a13a70";
    private static final String EXPECTED_SAFE = "0xd93b25cb943d14d0d34fbaf01fc93a0f8b5f6e47";

    @Test
    @DisplayName("TC-WD-001: deriveProxyWallet matches the official test vector")
    void proxyWalletMatchesVector() {
        SigningIdentity.ProxyWallet identity = SigningIdentity.deriveProxyWallet(EOA);

        assertEquals(EXPECTED_PROXY, identity.maker());
        assertEquals(EOA.toLowerCase(java.util.Locale.ROOT), identity.signer());
        assertEquals(1, identity.signatureType());
    }

    @Test
    @DisplayName("TC-WD-002: deriveSafeWallet matches the official test vector")
    void safeWalletMatchesVector() {
        SigningIdentity.SafeWallet identity = SigningIdentity.deriveSafeWallet(EOA);

        assertEquals(EXPECTED_SAFE, identity.maker());
        assertEquals(EOA.toLowerCase(java.util.Locale.ROOT), identity.signer());
        assertEquals(2, identity.signatureType());
    }
}
