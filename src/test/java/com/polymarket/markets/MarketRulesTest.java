package com.polymarket.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarketRulesTest {

    private static MarketRules rules() {
        return new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false);
    }

    @Nested
    class Rules {

        @Test
        void shouldThrowIllegalArgumentExceptionWhenPriceIsOffGrid() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> rules().requireOnGrid(Price.of("0.535")));
            assertTrue(e.getMessage().contains("0.535"));
        }

        @Test
        void shouldAcceptPriceWhenPriceIsOnGrid() {
            assertEquals(Price.of("0.53"), rules().requireOnGrid(Price.of("0.53")));
        }

        @Test
        void shouldEnforceMinimumSharesWhenQuantityIsBelowOrAtMinimum() {
            assertThrows(IllegalArgumentException.class,
                    () -> rules().requireAtLeastMinimum(ShareQuantity.of("4.999999")));
            assertEquals(ShareQuantity.of("5"),
                    rules().requireAtLeastMinimum(ShareQuantity.of("5")));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenPriceIsAtUniversalBound() {
            assertThrows(IllegalArgumentException.class,
                    () -> rules().requireWithinBounds(Price.of("0")));
            assertThrows(IllegalArgumentException.class,
                    () -> rules().requireWithinBounds(Price.of("1")));
        }

        @Test
        void shouldEnforceMarketSpecificBoundsWhenPriceTouchesTradeableLimits() {
            MarketRules coarse = new MarketRules(TickSize.of("0.1"), ShareQuantity.of("5"), false);

            assertEquals(Price.of("0.1"), coarse.requireWithinBounds(Price.of("0.1")));
            assertEquals(Price.of("0.9"), coarse.requireWithinBounds(Price.of("0.9")));
            assertThrows(IllegalArgumentException.class,
                    () -> coarse.requireWithinBounds(Price.of("0.05")));
            assertThrows(IllegalArgumentException.class,
                    () -> coarse.requireWithinBounds(Price.of("0.95")));
        }

        @Test
        void shouldCalculateNotionalWhenUsingDocumentedAmountPrecision() {
            // Official: round the amount up to amount decimals + 4, then down to amount decimals.
            // A 0.01 market carries 4 amount decimals, so 0.52 x 21.538461 = 11.19999972 -> 11.1999.
            assertEquals(PusdAmount.of("11.1999"),
                    rules().notional(Price.of("0.52"), ShareQuantity.of("21.538461")));
            assertEquals(PusdAmount.of("5.2"),
                    rules().notional(Price.of("0.52"), ShareQuantity.of("10")));
        }

        @Test
        void shouldExpressMinimumInSharesWhenRuleIsConstructed() {
            // Gamma's notional minimum must never reach signing; the rule is expressed in shares.
            assertEquals("5", rules().minimumShares().toString());
        }
    }

    @Nested
    class Book {

        private OrderBookSnapshot shuffled() {
            return new OrderBookSnapshot(
                    "0xcondition", new TokenId("12345"), Instant.ofEpochSecond(1773890758L), "hash-1",
                    List.of(level("0.40", "10"), level("0.44", "5"), level("0.42", "7")),
                    List.of(level("0.55", "9"), level("0.51", "4"), level("0.53", "6")),
                    rules(), Optional.of(Price.of("0.50")));
        }

        private PriceLevel level(String price, String size) {
            return new PriceLevel(Price.of(price), ShareQuantity.of(size));
        }

        @Test
        void shouldSortBidsDescendingWhenWireOrderIsShuffled() {
            assertEquals(List.of(Price.of("0.44"), Price.of("0.42"), Price.of("0.40")),
                    shuffled().bids().stream().map(PriceLevel::price).toList());
        }

        @Test
        void shouldSortAsksAscendingWhenWireOrderIsShuffled() {
            assertEquals(List.of(Price.of("0.51"), Price.of("0.53"), Price.of("0.55")),
                    shuffled().asks().stream().map(PriceLevel::price).toList());
        }

        @Test
        void shouldExposeTopOfBookWhenBookSidesAreSorted() {
            assertEquals(Price.of("0.44"), shuffled().bestBid().orElseThrow().price());
            assertEquals(Price.of("0.51"), shuffled().bestAsk().orElseThrow().price());
        }

        @Test
        void shouldLeaveBestLevelEmptyWhenBookSideIsEmpty() {
            OrderBookSnapshot empty = new OrderBookSnapshot(
                    "0xcondition", new TokenId("12345"), Instant.EPOCH, "hash-1",
                    List.of(), List.of(), rules(), Optional.of(Price.of("0.50")));
            assertTrue(empty.bestBid().isEmpty());
            assertTrue(empty.bestAsk().isEmpty());
        }

        @Test
        void shouldCarrySigningRulesWhenSnapshotIsConstructed() {
            OrderBookSnapshot book = shuffled();
            assertEquals(TickSize.of("0.01"), book.rules().tickSize());
            assertEquals(ShareQuantity.of("5"), book.rules().minimumShares());
            assertFalse(book.rules().negativeRisk());
            assertEquals(Instant.ofEpochSecond(1773890758L), book.observedAt());
        }

        @Test
        void shouldThrowUnsupportedOperationExceptionWhenLevelsAreMutated() {
            assertThrows(UnsupportedOperationException.class,
                    () -> shuffled().bids().add(level("0.99", "1")));
        }
    }
}
