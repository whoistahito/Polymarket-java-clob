package com.polymarket.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Exact market values")
class ExactValuesTest {

    @Nested
    @DisplayName("Price")
    class Prices {

        @Test
        @DisplayName("TC-MV-001: a price outside the probability range is rejected")
        void rejectsOutsideProbabilityRange() {
            assertThrows(IllegalArgumentException.class, () -> Price.of("-0.01"));
            assertThrows(IllegalArgumentException.class, () -> Price.of("1.01"));
        }

        @Test
        @DisplayName("TC-MV-002: the probability bounds themselves are valid")
        void acceptsBounds() {
            assertEquals(new BigDecimal("0"), Price.of("0").value());
            assertEquals(new BigDecimal("1"), Price.of("1").value());
        }

        @Test
        @DisplayName("TC-MV-003: equal prices written differently compare equal")
        void comparesByNumericValue() {
            assertEquals(Price.of("0.50"), Price.of("0.5"));
            assertEquals(Price.of("0.50").hashCode(), Price.of("0.5").hashCode());
            assertNotEquals(Price.of("0.50"), Price.of("0.51"));
        }

        @Test
        @DisplayName("TC-MV-004: a price never becomes a double")
        void keepsExactDecimal() {
            assertEquals(new BigDecimal("0.0001"), Price.of("0.0001").value());
        }
    }

    @Nested
    @DisplayName("TickSize")
    class Ticks {

        @ParameterizedTest(name = "tick {0}")
        @ValueSource(strings = {"0.1", "0.01", "0.005", "0.0025", "0.001", "0.0001"})
        @DisplayName("TC-MV-005: all six documented ticks are supported")
        void supportsTheDocumentedGrid(String tick) {
            assertEquals(new BigDecimal(tick), TickSize.of(tick).value());
        }

        @Test
        @DisplayName("TC-MV-006: a tick is matched by numeric value, not by text")
        void matchesNumerically() {
            assertEquals(TickSize.of("0.01"), TickSize.of("0.010"));
            assertEquals(TickSize.of("0.01"), TickSize.of("0.0100"));
        }

        @Test
        @DisplayName("TC-MV-007: an unrecognised tick throws rather than falling back")
        void rejectsUnknownTick() {
            // A fallback profile would mis-price every order on the market it guessed wrong.
            assertThrows(IllegalArgumentException.class, () -> TickSize.of("0.02"));
            assertThrows(IllegalArgumentException.class, () -> TickSize.of("0"));
        }

        @Test
        @DisplayName("TC-MV-008a: the supported grid matches the pinned protocol fixture")
        void gridMatchesPinnedFixture() throws Exception {
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
        @DisplayName("TC-MV-008: a tick knows whether a price sits on its grid")
        void detectsOffGridPrices() {
            assertTrue(TickSize.of("0.01").isOnGrid(Price.of("0.53")));
            assertTrue(TickSize.of("0.0025").isOnGrid(Price.of("0.5025")));
            assertEquals(false, TickSize.of("0.01").isOnGrid(Price.of("0.535")));
            assertEquals(false, TickSize.of("0.0025").isOnGrid(Price.of("0.5030")));
        }
    }

    @Nested
    @DisplayName("quantities")
    class Quantities {

        @Test
        @DisplayName("TC-MV-009: negative amounts are rejected in both units")
        void rejectsNegatives() {
            assertThrows(IllegalArgumentException.class, () -> ShareQuantity.of("-1"));
            assertThrows(IllegalArgumentException.class, () -> PusdAmount.of("-0.000001"));
        }

        @Test
        @DisplayName("TC-MV-010: more than six decimals cannot be represented")
        void rejectsSubUnitPrecision() {
            assertThrows(IllegalArgumentException.class, () -> ShareQuantity.of("1.0000001"));
            assertThrows(IllegalArgumentException.class, () -> PusdAmount.of("1.0000001"));
        }

        @Test
        @DisplayName("TC-MV-011: quantities convert to exact six-decimal base units")
        void convertsToBaseUnits() {
            assertEquals(10_000_000L, ShareQuantity.of("10").baseUnits());
            assertEquals(1_932_381L, PusdAmount.of("1.932381").baseUnits());
        }

        @Test
        @DisplayName("TC-MV-012: shares and pUSD are different types for the same number")
        void unitsAreNotInterchangeable() {
            assertNotEquals(ShareQuantity.of("10"), (Object) PusdAmount.of("10"));
        }
    }

    @Nested
    @DisplayName("asset identifiers")
    class AssetIds {

        @Test
        @DisplayName("TC-MV-013: a token and a position with the same digits are different assets")
        void tokenAndPositionAreDistinct() {
            String digits = "713210456792522125946263855327069127503327285719425322896313793124555839925";
            AssetId token = new TokenId(digits);
            AssetId position = new PositionId(digits);

            assertNotEquals(token, (Object) position);
            assertTrue(token instanceof TokenId);
            assertTrue(position instanceof PositionId);
        }

        @Test
        @DisplayName("TC-MV-014: an asset identifier must be a positive integer")
        void rejectsMalformedIdentifiers() {
            assertThrows(IllegalArgumentException.class, () -> new TokenId(""));
            assertThrows(IllegalArgumentException.class, () -> new TokenId("0xabc"));
            assertThrows(IllegalArgumentException.class, () -> new PositionId("-1"));
        }
    }
}
