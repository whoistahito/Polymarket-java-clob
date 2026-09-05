package com.polymarket.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExactValuesTest {

    @Nested
    class Prices {

        @Test
        void shouldThrowIllegalArgumentExceptionWhenPriceIsOutsideProbabilityRange() {
            assertThrows(IllegalArgumentException.class, () -> Price.of("-0.01"));
            assertThrows(IllegalArgumentException.class, () -> Price.of("1.01"));
        }

        @Test
        void shouldAcceptBoundsWhenPriceIsZeroOrOne() {
            assertEquals(new BigDecimal("0"), Price.of("0").value());
            assertEquals(new BigDecimal("1"), Price.of("1").value());
        }

        @Test
        void shouldComparePricesByNumericValueWhenTextDiffers() {
            assertEquals(Price.of("0.50"), Price.of("0.5"));
            assertEquals(Price.of("0.50").hashCode(), Price.of("0.5").hashCode());
            assertNotEquals(Price.of("0.50"), Price.of("0.51"));
        }

        @Test
        void shouldKeepExactDecimalWhenPriceHasFourDecimals() {
            assertEquals(new BigDecimal("0.0001"), Price.of("0.0001").value());
        }
    }

    @Nested
    class Ticks {

        @ParameterizedTest(name = "tick {0}")
        @ValueSource(strings = {"0.1", "0.01", "0.005", "0.0025", "0.001", "0.0001"})
        void shouldSupportDocumentedTickGridWhenTickIsRecognized(String tick) {
            assertEquals(new BigDecimal(tick), TickSize.of(tick).value());
        }

        @Test
        void shouldMatchTickNumericallyWhenTextScaleDiffers() {
            assertEquals(TickSize.of("0.01"), TickSize.of("0.010"));
            assertEquals(TickSize.of("0.01"), TickSize.of("0.0100"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenTickIsUnrecognized() {
            assertThrows(IllegalArgumentException.class, () -> TickSize.of("0.02"));
            assertThrows(IllegalArgumentException.class, () -> TickSize.of("0"));
        }

        @Test
        void shouldMatchTickGridWhenUsingPinnedFixture() throws Exception {
            java.util.List<String> pinned = new java.util.ArrayList<>();
            try (java.io.InputStream in = getClass().getResourceAsStream("/protocol/constraints.json")) {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(in)
                        .path("tickGrid").path("values").forEach(n -> pinned.add(n.asText()));
            }
            for (String tick : pinned) {
                assertEquals(new BigDecimal(tick), TickSize.of(tick).value());
            }
            assertEquals(6, pinned.size(), "the documented grid has six ticks");
        }

        @Test
        void shouldDetectOffGridPricesWhenPriceMissesTick() {
            assertTrue(TickSize.of("0.01").isOnGrid(Price.of("0.53")));
            assertTrue(TickSize.of("0.0025").isOnGrid(Price.of("0.5025")));
            assertEquals(false, TickSize.of("0.01").isOnGrid(Price.of("0.535")));
            assertEquals(false, TickSize.of("0.0025").isOnGrid(Price.of("0.5030")));
        }
    }

    @Nested
    class Quantities {

        @Test
        void shouldThrowIllegalArgumentExceptionWhenQuantityIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> ShareQuantity.of("-1"));
            assertThrows(IllegalArgumentException.class, () -> PusdAmount.of("-0.000001"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenQuantityExceedsSixDecimals() {
            assertThrows(IllegalArgumentException.class, () -> ShareQuantity.of("1.0000001"));
            assertThrows(IllegalArgumentException.class, () -> PusdAmount.of("1.0000001"));
        }

        @Test
        void shouldConvertQuantitiesWhenUsingSixDecimalBaseUnits() {
            assertEquals(10_000_000L, ShareQuantity.of("10").baseUnits());
            assertEquals(1_932_381L, PusdAmount.of("1.932381").baseUnits());
        }

        @Test
        void shouldKeepUnitsDistinctWhenNumericValuesMatch() {
            assertNotEquals(ShareQuantity.of("10"), (Object) PusdAmount.of("10"));
        }
    }

    @Nested
    class AssetIds {

        @Test
        void shouldKeepAssetTypesDistinctWhenDigitsMatch() {
            String digits = "713210456792522125946263855327069127503327285719425322896313793124555839925";
            AssetId token = new TokenId(digits);
            AssetId position = new PositionId(digits);

            assertNotEquals(token, (Object) position);
            assertTrue(token instanceof TokenId);
            assertTrue(position instanceof PositionId);
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenAssetIdentifierIsMalformed() {
            assertThrows(IllegalArgumentException.class, () -> new TokenId(""));
            assertThrows(IllegalArgumentException.class, () -> new TokenId("0xabc"));
            assertThrows(IllegalArgumentException.class, () -> new PositionId("-1"));
        }
    }
}
