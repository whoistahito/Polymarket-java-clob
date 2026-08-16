package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.markets.MarketRules;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.Price;
import com.polymarket.markets.PriceLevel;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Immediate depth planning")
class ImmediatePlannerTest {

    private static final TokenId ASSET = new TokenId("12345");

    private static PriceLevel level(String price, String size) {
        return new PriceLevel(Price.of(price), ShareQuantity.of(size));
    }

    /** Asks deliberately shuffled: the planner must not trust wire ordering. */
    private static OrderBookSnapshot book() {
        return new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH, "hash",
                List.of(level("0.48", "20"), level("0.50", "30"), level("0.46", "10")),
                List.of(level("0.54", "30"), level("0.50", "10"), level("0.52", "20")),
                new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false),
                Optional.of(Price.of("0.51")));
    }

    @Nested
    @DisplayName("immediate BUY")
    class Buys {

        @Test
        @DisplayName("TC-DP-001: a budget filled by the best level alone does not cross higher")
        void staysAtTheBestLevel() {
            // 0.50 x 10 available = 5.00 pUSD. A 4.00 budget buys 8 shares at 0.50.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("4"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(ShareQuantity.of("8"), executable.shares());
            assertEquals(PusdAmount.of("4"), executable.cost());
            assertEquals(Price.of("0.50"), executable.protectedPrice());
        }

        @Test
        @DisplayName("TC-DP-002: walking into a second level lifts the protected price to it")
        void protectedPriceIsTheWorstLevelTouched() {
            // 0.50 x 10 = 5.00, then 6.00 more at 0.52 buys 11.538461 shares (truncated).
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.52"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("21.538461"), executable.shares());
        }

        @Test
        @DisplayName("TC-DP-003: a caller ceiling stops the walk instead of crossing past it")
        void callerCeilingCapsTheWalk() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("100"), ExecutionPolicy.FAK)
                            .notAbove(Price.of("0.50")), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.50"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("10"), executable.shares(), "only the 0.50 level is eligible");
            assertEquals(PusdAmount.of("5"), executable.cost());
        }

        @Test
        @DisplayName("TC-DP-004: FOK reports insufficient depth rather than filling partially")
        void fokRejectsInsufficientDepth() {
            // Whole book is 5.00 + 10.40 + 16.20 = 31.60 pUSD.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("50"), ExecutionPolicy.FOK), book());

            ImmediatePlan.InsufficientDepth insufficient =
                    assertInstanceOf(ImmediatePlan.InsufficientDepth.class, plan);
            assertEquals(PusdAmount.of("31.6"), insufficient.available());
        }

        @Test
        @DisplayName("TC-DP-005: FAK fills what is there and reports the shortfall")
        void fakFillsWhatIsAvailable() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("50"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(PusdAmount.of("31.6"), executable.cost());
            assertEquals(Price.of("0.54"), executable.protectedPrice());
            assertTrue(executable.partial(), "a FAK that could not spend the budget is partial");
        }

        @Test
        @DisplayName("TC-DP-006: a fee-aware budget leaves room for the fee")
        void feeAwareBudgetReservesTheFee() {
            // 100 bps on a 4.00 budget leaves 3.960396 spendable; at 0.50 that is 7.920792 shares.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("4"), ExecutionPolicy.FAK)
                            .withFeeRate(FeeRate.ofBasisPoints(100)), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertTrue(executable.cost().value().add(executable.fee().value())
                            .compareTo(PusdAmount.of("4").value()) <= 0,
                    "order value plus fee must stay inside the budget");
            assertEquals(ShareQuantity.of("7.920792"), executable.shares());
        }
    }

    @Nested
    @DisplayName("immediate SELL")
    class Sells {

        @Test
        @DisplayName("TC-DP-007: a sell walks bids best-first and protects at the worst level touched")
        void sellWalksBidsDescending() {
            // 30 shares: 30 at 0.50 exactly fills the best bid level.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("30"), ExecutionPolicy.FOK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.50"), executable.protectedPrice());
            assertEquals(PusdAmount.of("15"), executable.cost(), "proceeds for a sell");
        }

        @Test
        @DisplayName("TC-DP-008: a sell crossing two levels protects at the lower one")
        void sellProtectsAtTheLowestLevelTouched() {
            // 40 shares: 30 at 0.50 (15.00) + 10 at 0.48 (4.80) = 19.80.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("40"), ExecutionPolicy.FOK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.48"), executable.protectedPrice());
            assertEquals(PusdAmount.of("19.8"), executable.cost());
        }

        @Test
        @DisplayName("TC-DP-009: a caller floor stops the walk instead of selling below it")
        void callerFloorCapsTheWalk() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("100"), ExecutionPolicy.FAK)
                            .notBelow(Price.of("0.48")), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.48"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("50"), executable.shares(), "0.46 is below the floor");
        }

        @Test
        @DisplayName("TC-DP-010: FOK on a sell reports the shares actually available")
        void fokSellReportsAvailableShares() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("100"), ExecutionPolicy.FOK), book());

            ImmediatePlan.InsufficientDepth insufficient =
                    assertInstanceOf(ImmediatePlan.InsufficientDepth.class, plan);
            assertEquals(ShareQuantity.of("60"), insufficient.availableShares().orElseThrow());
        }
    }

    @Nested
    @DisplayName("empty book")
    class Empty {

        @Test
        @DisplayName("TC-DP-011: an empty side yields insufficient depth, never a fabricated price")
        void emptyBookCannotBePlanned() {
            OrderBookSnapshot empty = new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH, "hash",
                    List.of(), List.of(),
                    new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false),
                    Optional.of(Price.of("0.51")));

            assertInstanceOf(ImmediatePlan.InsufficientDepth.class, ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("10"), ExecutionPolicy.FAK), empty));
        }
    }
}
