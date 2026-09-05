package com.polymarket.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Proves CREATE2 wallet derivation against the rs-clob-client/src/lib.rs vectors. */
class WalletDerivationTest {

    private static final String EOA = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final String EXPECTED_PROXY = "0x365f0ca36ae1f641e02fe3b7743673da42a13a70";
    private static final String EXPECTED_SAFE = "0xd93b25cb943d14d0d34fbaf01fc93a0f8b5f6e47";

    @Test
    void shouldMatchProxyWalletWhenUsingOfficialVector() {
        SigningIdentity.ProxyWallet identity = SigningIdentity.deriveProxyWallet(EOA);

        assertEquals(EXPECTED_PROXY, identity.tradingWallet());
        assertEquals(EOA.toLowerCase(java.util.Locale.ROOT), identity.accountSigner());
        assertEquals(1, identity.signatureType());
    }

    @Test
    void shouldMatchSafeWalletWhenUsingOfficialVector() {
        SigningIdentity.SafeWallet identity = SigningIdentity.deriveSafeWallet(EOA);

        assertEquals(EXPECTED_SAFE, identity.tradingWallet());
        assertEquals(EOA.toLowerCase(java.util.Locale.ROOT), identity.accountSigner());
        assertEquals(2, identity.signatureType());
    }
}
