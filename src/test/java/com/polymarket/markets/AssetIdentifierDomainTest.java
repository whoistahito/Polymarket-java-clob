package com.polymarket.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Asset identifiers follow the protocol's full unsigned uint256 range. */
class AssetIdentifierDomainTest {

    static final String TWO_POW_255 = "5789604461865809771178549250434395392663"
            + "4992332820282019728792003956564819968";
    static final String MAX_UINT256 = "11579208923731619542357098500868790785326"
            + "9984665640564039457584007913129639935";
    static final String TWO_POW_256 = "11579208923731619542357098500868790785326"
            + "9984665640564039457584007913129639936";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "1", TWO_POW_255, MAX_UINT256})
    void shouldAcceptUnsignedDomainWhenValueFitsUint256(String digits) {
        assertEquals(digits, new TokenId(digits).value());
        assertEquals(digits, new PositionId(digits).value());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "   ", "-1", "0xabc", "1.0", TWO_POW_256, TWO_POW_256 + "0"})
    void shouldThrowIllegalArgumentExceptionWhenValueExceedsUnsignedDomain(String digits) {
        assertThrows(IllegalArgumentException.class, () -> new TokenId(digits));
        assertThrows(IllegalArgumentException.class, () -> new PositionId(digits));
    }
}
