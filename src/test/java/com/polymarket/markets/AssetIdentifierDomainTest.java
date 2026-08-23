package com.polymarket.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The protocol types an asset identifier as uint256, so the whole unsigned 256-bit range is a
 * legal identifier. Boundary literals are written out, never recomputed from a power.
 */
@DisplayName("Asset identifier domain")
class AssetIdentifierDomainTest {

    static final String TWO_POW_255 = "5789604461865809771178549250434395392663"
            + "4992332820282019728792003956564819968";
    static final String MAX_UINT256 = "11579208923731619542357098500868790785326"
            + "9984665640564039457584007913129639935";
    static final String TWO_POW_256 = "11579208923731619542357098500868790785326"
            + "9984665640564039457584007913129639936";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "1", TWO_POW_255, MAX_UINT256})
    @DisplayName("TC-AI-001: every uint256 value is a usable asset identifier")
    void acceptsTheWholeUnsignedDomain(String digits) {
        assertEquals(digits, new TokenId(digits).value());
        assertEquals(digits, new PositionId(digits).value());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "   ", "-1", "0xabc", "1.0", TWO_POW_256, TWO_POW_256 + "0"})
    @DisplayName("TC-AI-002: a value outside the uint256 domain is not an asset identifier")
    void rejectsValuesOutsideTheUnsignedDomain(String digits) {
        assertThrows(IllegalArgumentException.class, () -> new TokenId(digits));
        assertThrows(IllegalArgumentException.class, () -> new PositionId(digits));
    }
}
