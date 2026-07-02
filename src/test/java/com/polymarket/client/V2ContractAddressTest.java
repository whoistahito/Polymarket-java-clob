package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PMK-003 — verifying contract is selected by resolved protocol version and neg-risk flag.
 *
 * <p>Addresses are checksummed mixed-case and must be returned exactly as stored.
 */
class V2ContractAddressTest {

    @DisplayName("TC-V2A-001 resolveVerifyingContract returns correct address per (chain, version, negRisk)")
    @ParameterizedTest(name = "chain={0} version={1} negRisk={2} -> {3}")
    @CsvSource({
        "137,   1, false, 0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E",
        "137,   1, true,  0xC5d563A36AE78145C45a50134d48A1215220f80a",
        "137,   2, false, 0xE111180000d2663C0091e4f400237545B87B996B",
        "137,   2, true,  0xe2222d279d744050d28e00520010520000310F59",
        "80002, 2, false, 0xE111180000d2663C0091e4f400237545B87B996B",
    })
    void resolvesVerifyingContract(int chainId, int version, boolean negRisk, String expected) {
        assertEquals(expected, OrderBuilder.resolveVerifyingContract(chainId, version, negRisk));
    }

    @DisplayName("TC-V2A-002 unsupported chain id throws IllegalArgumentException")
    @Test
    void unsupportedChainThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> OrderBuilder.resolveVerifyingContract(1, 1, false)
        );
    }
}
