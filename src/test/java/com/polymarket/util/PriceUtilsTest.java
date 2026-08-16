package com.polymarket.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for PriceUtils.
 * Verifies price calculations, tick rounding, and utility methods.
 */
@DisplayName("PriceUtils Tests")
class PriceUtilsTest {

    @Test
    @DisplayName("TC-PU-001: Tick round up")
    void testTickRoundUp() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.503"),
                new BigDecimal("0.01"),
                "up"
        );

        assertEquals(new BigDecimal("0.51"), result);
    }

    @Test
    @DisplayName("TC-PU-001b: Tick round up at exact tick boundary")
    void testTickRoundUpAtBoundary() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.50"),
                new BigDecimal("0.01"),
                "up"
        );

        assertEquals(new BigDecimal("0.50"), result);
    }

    @Test
    @DisplayName("TC-PU-002: Tick round down")
    void testTickRoundDown() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.508"),
                new BigDecimal("0.01"),
                "down"
        );

        assertEquals(new BigDecimal("0.50"), result);
    }

    @Test
    @DisplayName("TC-PU-002b: Tick round down at exact tick boundary")
    void testTickRoundDownAtBoundary() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.51"),
                new BigDecimal("0.01"),
                "down"
        );

        assertEquals(new BigDecimal("0.51"), result);
    }

    @Test
    @DisplayName("TC-PU-003: Tick round nearest - rounds down when below midpoint")
    void testTickRoundNearestRoundsDown() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.504"),
                new BigDecimal("0.01"),
                "nearest"
        );

        assertEquals(new BigDecimal("0.50"), result);
    }

    @Test
    @DisplayName("TC-PU-003b: Tick round nearest - rounds up when at or above midpoint")
    void testTickRoundNearestRoundsUp() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.505"),
                new BigDecimal("0.01"),
                "nearest"
        );

        assertEquals(new BigDecimal("0.51"), result);
    }

    @Test
    @DisplayName("TC-PU-003c: Tick round nearest with default mode")
    void testTickRoundNearestDefaultMode() {
        BigDecimal result = PriceUtils.tickRound(
                new BigDecimal("0.506"),
                new BigDecimal("0.01"),
                "unknown"
        );

        // Default should be nearest (HALF_UP)
        assertEquals(new BigDecimal("0.51"), result);
    }

    @Test
    @DisplayName("TC-PU-004: Fee rate conversion from BPS")
    void testFeeRateFromBps() {
        // 100 bps = 1% = 0.01
        assertEquals(0, new BigDecimal("0.01").compareTo(
                PriceUtils.feeRateFromBps(100).setScale(2, RoundingMode.HALF_UP)));

        // 10 bps = 0.1% = 0.001
        assertEquals(0, new BigDecimal("0.001").compareTo(
                PriceUtils.feeRateFromBps(10).setScale(3, RoundingMode.HALF_UP)));

        // 50 bps = 0.5% = 0.005
        assertEquals(0, new BigDecimal("0.005").compareTo(
                PriceUtils.feeRateFromBps(50).setScale(3, RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("TC-PU-004b: Fee rate from zero BPS")
    void testFeeRateFromZeroBps() {
        assertEquals(0, BigDecimal.ZERO.compareTo(PriceUtils.feeRateFromBps(0)));
    }

    @Test
    @DisplayName("TC-PU-005: Valid price check - valid prices")
    void testIsValidPriceValidPrices() {
        assertTrue(PriceUtils.isValidPrice(new BigDecimal("0.5")));
        assertTrue(PriceUtils.isValidPrice(new BigDecimal("0.01")));
        assertTrue(PriceUtils.isValidPrice(new BigDecimal("0.99")));
        assertTrue(PriceUtils.isValidPrice(BigDecimal.ZERO));
        assertTrue(PriceUtils.isValidPrice(BigDecimal.ONE));
    }

    @Test
    @DisplayName("TC-PU-005b: Valid price check - invalid prices")
    void testIsValidPriceInvalidPrices() {
        assertFalse(PriceUtils.isValidPrice(new BigDecimal("-0.1")));
        assertFalse(PriceUtils.isValidPrice(new BigDecimal("1.1")));
        assertFalse(PriceUtils.isValidPrice(new BigDecimal("-1")));
        assertFalse(PriceUtils.isValidPrice(new BigDecimal("100")));
        assertFalse(PriceUtils.isValidPrice(null));
    }

    @Test
    @DisplayName("TC-PU-007: Tick round with null price returns null")
    void testTickRoundNullPrice() {
        BigDecimal result = PriceUtils.tickRound(null, new BigDecimal("0.01"), "nearest");
        assertNull(result);
    }

    @Test
    @DisplayName("TC-PU-008: Tick round with null tick size returns price unchanged")
    void testTickRoundNullTickSize() {
        BigDecimal price = new BigDecimal("0.505");
        BigDecimal result = PriceUtils.tickRound(price, null, "nearest");
        assertEquals(price, result);
    }

    @Test
    @DisplayName("TC-PU-009: Tick round with zero tick size returns price unchanged")
    void testTickRoundZeroTickSize() {
        BigDecimal price = new BigDecimal("0.505");
        BigDecimal result = PriceUtils.tickRound(price, BigDecimal.ZERO, "nearest");
        assertEquals(price, result);
    }

    @Test
    @DisplayName("TC-PU-010: Tick round with negative tick size returns price unchanged")
    void testTickRoundNegativeTickSize() {
        BigDecimal price = new BigDecimal("0.505");
        BigDecimal result = PriceUtils.tickRound(price, new BigDecimal("-0.01"), "nearest");
        assertEquals(price, result);
    }

    @Test
    @DisplayName("TC-PU-011: Safe BigDecimal parsing - valid values")
    void testSafeBigDecimalValidValues() {
        assertEquals(new BigDecimal("0.5"), PriceUtils.safeBigDecimal("0.5"));
        assertEquals(new BigDecimal("100"), PriceUtils.safeBigDecimal("100"));
        assertEquals(new BigDecimal("-5.5"), PriceUtils.safeBigDecimal("-5.5"));
        assertEquals(new BigDecimal("0"), PriceUtils.safeBigDecimal("0"));
    }

    @Test
    @DisplayName("TC-PU-012: Safe BigDecimal parsing - invalid values return null")
    void testSafeBigDecimalInvalidValues() {
        assertNull(PriceUtils.safeBigDecimal(null));
        assertNull(PriceUtils.safeBigDecimal(""));
        assertNull(PriceUtils.safeBigDecimal("   "));
        assertNull(PriceUtils.safeBigDecimal("not a number"));
        assertNull(PriceUtils.safeBigDecimal("12.34.56"));
    }

    @Test
    @DisplayName("TC-PU-013: Safe BigDecimal parsing with default value")
    void testSafeBigDecimalWithDefault() {
        BigDecimal defaultValue = new BigDecimal("99.99");

        assertEquals(new BigDecimal("0.5"), PriceUtils.safeBigDecimal("0.5", defaultValue));
        assertEquals(defaultValue, PriceUtils.safeBigDecimal(null, defaultValue));
        assertEquals(defaultValue, PriceUtils.safeBigDecimal("", defaultValue));
        assertEquals(defaultValue, PriceUtils.safeBigDecimal("invalid", defaultValue));
    }

    @Test
    @DisplayName("TC-PU-014: Format price with correct scale")
    void testFormatPrice() {
        assertEquals("0.5000", PriceUtils.formatPrice(new BigDecimal("0.5")));
        assertEquals("0.1234", PriceUtils.formatPrice(new BigDecimal("0.1234")));
        assertEquals("0.1235", PriceUtils.formatPrice(new BigDecimal("0.12345")));
        assertEquals("1.0000", PriceUtils.formatPrice(new BigDecimal("1")));
    }

    @Test
    @DisplayName("TC-PU-015: Format price with null returns 'null'")
    void testFormatPriceNull() {
        assertEquals("null", PriceUtils.formatPrice(null));
    }

    @Test
    @DisplayName("TC-PU-016: Format money with dollar sign")
    void testFormatMoney() {
        assertEquals("$0.5000", PriceUtils.formatMoney(new BigDecimal("0.5")));
        assertEquals("$100.0000", PriceUtils.formatMoney(new BigDecimal("100")));
    }

    @Test
    @DisplayName("TC-PU-017: Format money with null returns '$null'")
    void testFormatMoneyNull() {
        assertEquals("$null", PriceUtils.formatMoney(null));
    }

    @Test
    @DisplayName("TC-PU-018: Format percentage")
    void testFormatPercentage() {
        assertEquals("5.25%", PriceUtils.formatPercentage(new BigDecimal("5.25")));
        assertEquals("100.00%", PriceUtils.formatPercentage(new BigDecimal("100")));
        assertEquals("0.50%", PriceUtils.formatPercentage(new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("TC-PU-019: Format percentage with null returns 'null%'")
    void testFormatPercentageNull() {
        assertEquals("null%", PriceUtils.formatPercentage(null));
    }

    @Test
    @DisplayName("TC-PU-020: Equal within tolerance - values are equal")
    void testEqualWithinToleranceEqual() {
        assertTrue(PriceUtils.equalWithinTolerance(
                new BigDecimal("0.50"),
                new BigDecimal("0.51"),
                new BigDecimal("0.02")
        ));

        assertTrue(PriceUtils.equalWithinTolerance(
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("0.001")
        ));
    }

    @Test
    @DisplayName("TC-PU-021: Equal within tolerance - values are not equal")
    void testEqualWithinToleranceNotEqual() {
        assertFalse(PriceUtils.equalWithinTolerance(
                new BigDecimal("0.50"),
                new BigDecimal("0.60"),
                new BigDecimal("0.05")
        ));
    }

    @Test
    @DisplayName("TC-PU-022: Equal within tolerance - null handling")
    void testEqualWithinToleranceNull() {
        assertFalse(PriceUtils.equalWithinTolerance(null, new BigDecimal("0.5"), new BigDecimal("0.01")));
        assertFalse(PriceUtils.equalWithinTolerance(new BigDecimal("0.5"), null, new BigDecimal("0.01")));
        assertTrue(PriceUtils.equalWithinTolerance(null, null, new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("TC-PU-023: Clamp value - within range")
    void testClampWithinRange() {
        BigDecimal result = PriceUtils.clamp(
                new BigDecimal("0.5"),
                new BigDecimal("0.0"),
                new BigDecimal("1.0")
        );
        assertEquals(new BigDecimal("0.5"), result);
    }

    @Test
    @DisplayName("TC-PU-024: Clamp value - below minimum")
    void testClampBelowMinimum() {
        BigDecimal result = PriceUtils.clamp(
                new BigDecimal("-0.5"),
                new BigDecimal("0.0"),
                new BigDecimal("1.0")
        );
        assertEquals(new BigDecimal("0.0"), result);
    }

    @Test
    @DisplayName("TC-PU-025: Clamp value - above maximum")
    void testClampAboveMaximum() {
        BigDecimal result = PriceUtils.clamp(
                new BigDecimal("1.5"),
                new BigDecimal("0.0"),
                new BigDecimal("1.0")
        );
        assertEquals(new BigDecimal("1.0"), result);
    }

    @Test
    @DisplayName("TC-PU-026: Calculate required amount")
    void testCalculateRequiredAmount() {
        BigDecimal result = PriceUtils.calculateRequiredAmount(
                new BigDecimal("0.50"),
                new BigDecimal("100")
        );
        assertEquals(new BigDecimal("50.00"), result);
    }

    @Test
    @DisplayName("TC-PU-027: Round with scale and mode")
    void testRoundWithScaleAndMode() {
        assertEquals(new BigDecimal("0.51"),
                PriceUtils.round(new BigDecimal("0.505"), 2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.50"),
                PriceUtils.round(new BigDecimal("0.505"), 2, RoundingMode.DOWN));
        assertEquals(new BigDecimal("0.51"),
                PriceUtils.round(new BigDecimal("0.501"), 2, RoundingMode.UP));
    }

    @Test
    @DisplayName("TC-PU-028: Round with null returns null")
    void testRoundWithNull() {
        assertNull(PriceUtils.round(null, 2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("TC-PU-029: Max of two values")
    void testMax() {
        assertEquals(new BigDecimal("0.6"),
                PriceUtils.max(new BigDecimal("0.5"), new BigDecimal("0.6")));
        assertEquals(new BigDecimal("0.6"),
                PriceUtils.max(new BigDecimal("0.6"), new BigDecimal("0.5")));
        assertEquals(new BigDecimal("0.5"),
                PriceUtils.max(new BigDecimal("0.5"), new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("TC-PU-030: Max with null handling")
    void testMaxWithNull() {
        assertEquals(new BigDecimal("0.5"), PriceUtils.max(null, new BigDecimal("0.5")));
        assertEquals(new BigDecimal("0.5"), PriceUtils.max(new BigDecimal("0.5"), null));
        assertNull(PriceUtils.max(null, null));
    }

    @Test
    @DisplayName("TC-PU-031: Min of two values")
    void testMin() {
        assertEquals(new BigDecimal("0.5"),
                PriceUtils.min(new BigDecimal("0.5"), new BigDecimal("0.6")));
        assertEquals(new BigDecimal("0.5"),
                PriceUtils.min(new BigDecimal("0.6"), new BigDecimal("0.5")));
        assertEquals(new BigDecimal("0.5"),
                PriceUtils.min(new BigDecimal("0.5"), new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("TC-PU-032: Min with null handling")
    void testMinWithNull() {
        assertEquals(new BigDecimal("0.5"), PriceUtils.min(null, new BigDecimal("0.5")));
        assertEquals(new BigDecimal("0.5"), PriceUtils.min(new BigDecimal("0.5"), null));
        assertNull(PriceUtils.min(null, null));
    }

    @Test
    @DisplayName("TC-PU-036: Constants are correct")
    void testConstants() {
        assertEquals(BigDecimal.ZERO, PriceUtils.ZERO);
        assertEquals(BigDecimal.ONE, PriceUtils.ONE);
        assertEquals(new BigDecimal("10000"), PriceUtils.TEN_THOUSAND);
    }

    @Test
    @DisplayName("TC-PU-037: Math context has correct precision")
    void testMathContextPrecision() {
        assertEquals(18, PriceUtils.MATH_CONTEXT.getPrecision());
        assertEquals(RoundingMode.HALF_UP, PriceUtils.MATH_CONTEXT.getRoundingMode());
    }

    @Test
    @DisplayName("TC-PU-038: Different tick sizes work correctly")
    void testDifferentTickSizes() {
        // Tick size 0.001
        assertEquals(new BigDecimal("0.505"),
                PriceUtils.tickRound(new BigDecimal("0.5051"), new BigDecimal("0.001"), "nearest"));

        // Tick size 0.1
        assertEquals(new BigDecimal("0.5"),
                PriceUtils.tickRound(new BigDecimal("0.54"), new BigDecimal("0.1"), "nearest"));
        assertEquals(new BigDecimal("0.6"),
                PriceUtils.tickRound(new BigDecimal("0.55"), new BigDecimal("0.1"), "nearest"));

        // Tick size 0.0001
        assertEquals(new BigDecimal("0.5051"),
                PriceUtils.tickRound(new BigDecimal("0.50509"), new BigDecimal("0.0001"), "nearest"));
    }

    // -----------------------------------------------------------------------
    // Phase 3 additions — priceValid, isTickSizeSmaller, generateOrderBookSummaryHash
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-PU-039: priceValid - price within valid range")
    void priceValidInRange() {
        // For tickSize=0.01: valid range is [0.01, 0.99]
        assertTrue(PriceUtils.priceValid(new BigDecimal("0.50"), "0.01"));
        assertTrue(PriceUtils.priceValid(new BigDecimal("0.01"), "0.01")); // at lower bound
        assertTrue(PriceUtils.priceValid(new BigDecimal("0.99"), "0.01")); // at upper bound
    }

    @Test
    @DisplayName("TC-PU-040: priceValid - price outside valid range")
    void priceValidOutOfRange() {
        // Below lower bound
        assertFalse(PriceUtils.priceValid(new BigDecimal("0.005"), "0.01"));
        // Above upper bound
        assertFalse(PriceUtils.priceValid(new BigDecimal("0.995"), "0.01"));
        // Zero
        assertFalse(PriceUtils.priceValid(BigDecimal.ZERO, "0.01"));
        // One
        assertFalse(PriceUtils.priceValid(BigDecimal.ONE, "0.01"));
    }

    @Test
    @DisplayName("TC-PU-041: priceValid - various tick sizes")
    void priceValidVariousTickSizes() {
        // 0.1 tick: valid [0.1, 0.9]
        assertTrue(PriceUtils.priceValid(new BigDecimal("0.5"), "0.1"));
        assertFalse(PriceUtils.priceValid(new BigDecimal("0.05"), "0.1"));
        assertFalse(PriceUtils.priceValid(new BigDecimal("0.95"), "0.1"));

        // 0.001 tick: valid [0.001, 0.999]
        assertTrue(PriceUtils.priceValid(new BigDecimal("0.999"), "0.001"));
        assertFalse(PriceUtils.priceValid(new BigDecimal("0.0005"), "0.001"));
    }

    @Test
    @DisplayName("TC-PU-042: priceValid - null arguments")
    void priceValidNullArgs() {
        assertFalse(PriceUtils.priceValid(null, "0.01"));
        assertFalse(PriceUtils.priceValid(new BigDecimal("0.5"), null));
    }

    @Test
    @DisplayName("TC-PU-043: isTickSizeSmaller - smaller tick size")
    void isTickSizeSmallerTrue() {
        assertTrue(PriceUtils.isTickSizeSmaller("0.001", "0.01"));
        assertTrue(PriceUtils.isTickSizeSmaller("0.0001", "0.1"));
        assertTrue(PriceUtils.isTickSizeSmaller("0.01", "0.1"));
    }

    @Test
    @DisplayName("TC-PU-044: isTickSizeSmaller - equal or larger tick size")
    void isTickSizeSmallerFalse() {
        assertFalse(PriceUtils.isTickSizeSmaller("0.01", "0.01")); // equal
        assertFalse(PriceUtils.isTickSizeSmaller("0.1", "0.01"));  // larger
        assertFalse(PriceUtils.isTickSizeSmaller("0.1", "0.001")); // much larger
    }

    @Test
    @DisplayName("TC-PU-045: isTickSizeSmaller - null arguments")
    void isTickSizeSmallerNullArgs() {
        assertFalse(PriceUtils.isTickSizeSmaller(null, "0.01"));
        assertFalse(PriceUtils.isTickSizeSmaller("0.01", null));
    }

    @Test
    @DisplayName("TC-PU-046: generateOrderBookSummaryHash returns non-empty hex string")
    void generateOrderBookSummaryHashNonEmpty() {
        com.polymarket.model.OrderBookSummary book = com.polymarket.model.OrderBookSummary.builder()
                .market("0xabc")
                .assetId("tok1")
                .timestamp("1700000000")
                .bids(java.util.List.of())
                .asks(java.util.List.of())
                .tickSize("0.01")
                .negRisk(false)
                .hash("")
                .build();

        String hash = PriceUtils.generateOrderBookSummaryHash(book);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        // SHA-1 produces a 40-character hex string
        assertEquals(40, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"), "hash should be lowercase hex");
    }

    @Test
    @DisplayName("TC-PU-047: generateOrderBookSummaryHash is deterministic")
    void generateOrderBookSummaryHashDeterministic() {
        com.polymarket.model.OrderBookSummary book = com.polymarket.model.OrderBookSummary.builder()
                .market("0xdef")
                .assetId("tok2")
                .timestamp("1700000001")
                .bids(java.util.List.of())
                .asks(java.util.List.of())
                .tickSize("0.01")
                .negRisk(false)
                .hash("ignored-on-input")
                .build();

        String hash1 = PriceUtils.generateOrderBookSummaryHash(book);
        String hash2 = PriceUtils.generateOrderBookSummaryHash(book);
        assertEquals(hash1, hash2, "hash should be deterministic");
    }

    @Test
    @DisplayName("TC-PU-048: generateOrderBookSummaryHash changes when content changes")
    void generateOrderBookSummaryHashChangesWithContent() {
        com.polymarket.model.OrderBookSummary book1 = com.polymarket.model.OrderBookSummary.builder()
                .market("0xaaa").assetId("t1").timestamp("100")
                .bids(java.util.List.of()).asks(java.util.List.of())
                .tickSize("0.01").negRisk(false).hash("").build();
        com.polymarket.model.OrderBookSummary book2 = com.polymarket.model.OrderBookSummary.builder()
                .market("0xbbb").assetId("t2").timestamp("200")
                .bids(java.util.List.of()).asks(java.util.List.of())
                .tickSize("0.01").negRisk(false).hash("").build();

        assertNotEquals(
            PriceUtils.generateOrderBookSummaryHash(book1),
            PriceUtils.generateOrderBookSummaryHash(book2)
        );
    }
}
