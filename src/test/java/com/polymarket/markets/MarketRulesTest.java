package com.polymarket.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Executable market context")
class MarketRulesTest {

    private static MarketRules rules() {
        return new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false);
    }

    @Nested
    @DisplayName("rules")
    class Rules {

        @Test
        @DisplayName("TC-MR-001: an off-grid price is rejected, never rounded")
        void rejectsOffGridPrice() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> rules().requireOnGrid(Price.of("0.535")));
            assertTrue(e.getMessage().contains("0.535"));
        }

        @Test
        @DisplayName("TC-MR-002: an on-grid price passes through unchanged")
        void acceptsOnGridPrice() {
            assertEquals(Price.of("0.53"), rules().requireOnGrid(Price.of("0.53")));
        }

        @Test
        @DisplayName("TC-MR-003: a quantity below the live CLOB minimum is rejected")
        void enforcesMinimumShares() {
            assertThrows(IllegalArgumentException.class,
                    () -> rules().requireAtLeastMinimum(ShareQuantity.of("4.999999")));
            assertEquals(ShareQuantity.of("5"),
                    rules().requireAtLeastMinimum(ShareQuantity.of("5")));
        }

        @Test
        @DisplayName("TC-MR-004: the minimum is expressed in shares, not notional")
        void minimumIsInShares() {
            // Gamma publishes a USDC notional minimum; it must never reach signing.
            assertEquals("5", rules().minimumShares().toString());
        }
    }

    @Nested
    @DisplayName("book snapshot")
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
        @DisplayName("TC-MR-005: bids sort best-first regardless of wire order")
        void bidsSortDescending() {
            assertEquals(List.of(Price.of("0.44"), Price.of("0.42"), Price.of("0.40")),
                    shuffled().bids().stream().map(PriceLevel::price).toList());
        }

        @Test
        @DisplayName("TC-MR-006: asks sort best-first regardless of wire order")
        void asksSortAscending() {
            assertEquals(List.of(Price.of("0.51"), Price.of("0.53"), Price.of("0.55")),
                    shuffled().asks().stream().map(PriceLevel::price).toList());
        }

        @Test
        @DisplayName("TC-MR-007: best bid and best ask come off the sorted book")
        void exposesTopOfBook() {
            assertEquals(Price.of("0.44"), shuffled().bestBid().orElseThrow().price());
            assertEquals(Price.of("0.51"), shuffled().bestAsk().orElseThrow().price());
        }

        @Test
        @DisplayName("TC-MR-008: an empty side has no best level rather than a fabricated one")
        void emptySideHasNoBest() {
            OrderBookSnapshot empty = new OrderBookSnapshot(
                    "0xcondition", new TokenId("12345"), Instant.EPOCH, "hash-1",
                    List.of(), List.of(), rules(), Optional.of(Price.of("0.50")));
            assertTrue(empty.bestBid().isEmpty());
            assertTrue(empty.bestAsk().isEmpty());
        }

        @Test
        @DisplayName("TC-MR-009: the snapshot carries its own rules, so signing needs no second read")
        void snapshotCarriesRules() {
            OrderBookSnapshot book = shuffled();
            assertEquals(TickSize.of("0.01"), book.rules().tickSize());
            assertEquals(ShareQuantity.of("5"), book.rules().minimumShares());
            assertFalse(book.rules().negativeRisk());
            assertEquals(Instant.ofEpochSecond(1773890758L), book.observedAt());
        }

        @Test
        @DisplayName("TC-MR-010: the level lists cannot be mutated after construction")
        void levelsAreImmutable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> shuffled().bids().add(level("0.99", "1")));
        }
    }
}
