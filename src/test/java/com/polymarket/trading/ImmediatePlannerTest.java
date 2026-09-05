package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    class Buys {

        @Test
        void shouldStayAtBestLevelWhenBudgetFitsFirstAsk() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("4"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(ShareQuantity.of("8"), executable.shares());
            assertEquals(PusdAmount.of("4"), executable.cost());
            assertEquals(Price.of("0.50"), executable.protectedPrice());
        }

        @Test
        void shouldRaiseProtectedPriceWhenBuyWalkReachesSecondLevel() {
            // Repricing every share at the protected level keeps the signed leg affordable.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.52"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("21.15"), executable.shares());
        }

        @Test
        void shouldStopAtCallerCeilingWhenBuyDepthExceedsIt() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("100"), ExecutionPolicy.FAK)
                            .notAbove(Price.of("0.50")), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.50"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("10"), executable.shares(), "only the 0.50 level is eligible");
            assertEquals(PusdAmount.of("5"), executable.cost());
        }

        @Test
        void shouldReportInsufficientDepthWhenFokBudgetExceedsBook() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("50"), ExecutionPolicy.FOK), book());

            ImmediatePlan.InsufficientDepth insufficient =
                    assertInstanceOf(ImmediatePlan.InsufficientDepth.class, plan);
            assertEquals(PusdAmount.of("31.6"), insufficient.available());
            assertEquals(ShareQuantity.of("60"), insufficient.availableShares().orElseThrow(),
                    "an all-or-nothing shortfall still reports what the book could have filled");
        }

        @Test
        void shouldFillAvailableDepthWhenFakBudgetExceedsBook() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("50"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.54"), executable.protectedPrice());
            // The order authorises all retained shares at the Protected Price it signs.
            assertEquals(PusdAmount.of("32.4"), executable.cost());
            assertTrue(executable.partial(), "a FAK that could not spend the budget is partial");
        }

        @Test
        void shouldReserveFeeWhenBuyBudgetIncludesTakerRate() {
            // The nonlinear taker fee must fit inside the budget before another level is crossed.
            PusdAmount budget = PusdAmount.of("5.175");
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, budget, ExecutionPolicy.FAK)
                            .withFeeRate(FeeRate.of("0.07")), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(ShareQuantity.of("10"), executable.shares());
            assertEquals(PusdAmount.of("5"), executable.cost());
            assertEquals(PusdAmount.of("0.175"), executable.fee());
            assertEquals(Price.of("0.50"), executable.protectedPrice(),
                    "the fee must not be funded by crossing into the next level");
            assertTrue(executable.cost().value().add(executable.fee().value())
                            .compareTo(budget.value()) <= 0,
                    "order value plus fee must stay inside the budget");
        }

        @Test
        void shouldBuyMoreWhenBudgetHasNoFeeRate() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("5.175"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(ShareQuantity.of("10"), executable.shares());
            assertEquals(Price.of("0.50"), executable.protectedPrice());
            assertEquals(PusdAmount.of("0"), executable.fee());
        }

        @Test
        void shouldKeepSpendInsideBudgetWhenBuyLegIsRepriced() {
            // The plan must be affordable at the Protected Price it actually signs, not a blend.
            PusdAmount budget = PusdAmount.of("11");
            MarketRules rules = book().rules();

            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateBuy.of(ASSET, budget, ExecutionPolicy.FAK), book()));

            assertEquals(Price.of("0.52"), plan.protectedPrice());
            assertTrue(rules.notional(plan.protectedPrice(), plan.shares()).value()
                            .add(plan.fee().value()).compareTo(budget.value()) <= 0,
                    "signing " + plan.shares() + " at " + plan.protectedPrice()
                            + " authorises more than the " + budget + " budget");
            assertEquals(plan.cost(), rules.notional(plan.protectedPrice(), plan.shares()),
                    "the reported cost is the pUSD leg the order will actually carry");
        }

        @Test
        void shouldKeepFeeAwareSpendInsideBudgetWhenBuyLegIsRepriced() {
            PusdAmount budget = PusdAmount.of("11");
            FeeRate rate = FeeRate.of("0.07");
            MarketRules rules = book().rules();

            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(ImmediateBuy.of(ASSET, budget, ExecutionPolicy.FAK)
                            .withFeeRate(rate), book()));

            assertTrue(rules.notional(plan.protectedPrice(), plan.shares()).value()
                            .add(plan.fee().value()).compareTo(budget.value()) <= 0,
                    "order value plus quoted fee must stay inside the budget");
        }

        @Test
        void shouldFollowSizePrecisionWhenPlanningShares() {
            // The official size grid rejects share quantities with more than its documented decimals.
            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK), book()));

            assertTrue(plan.shares().value().stripTrailingZeros().scale()
                            <= book().rules().tickSize().sizeDecimals(),
                    "planned " + plan.shares() + " carries more decimals than the tick allows");
        }
    }

    @Nested
    class Sells {

        @Test
        void shouldWalkBidsBestFirstWhenSellingAcrossDepth() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("30"), ExecutionPolicy.FOK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.50"), executable.protectedPrice());
            assertEquals(PusdAmount.of("15"), executable.cost(), "proceeds for a sell");
        }

        @Test
        void shouldProtectAtLowestLevelWhenSellCrossesTwoLevels() {
            // The signed floor is guaranteed proceeds; a blended walk is not.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("40"), ExecutionPolicy.FOK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.48"), executable.protectedPrice());
            assertEquals(PusdAmount.of("19.2"), executable.cost());
        }

        @Test
        void shouldStopAtCallerFloorWhenSellDepthFallsBelowIt() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("100"), ExecutionPolicy.FAK)
                            .notBelow(Price.of("0.48")), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.48"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("50"), executable.shares(), "0.46 is below the floor");
        }

        @Test
        void shouldReportAvailableSharesWhenFokSellLacksDepth() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("100"), ExecutionPolicy.FOK), book());

            ImmediatePlan.InsufficientDepth insufficient =
                    assertInstanceOf(ImmediatePlan.InsufficientDepth.class, plan);
            assertEquals(ShareQuantity.of("60"), insufficient.availableShares().orElseThrow());
        }

        @Test
        void shouldKeepAmountPrecisionWhenReportingInsufficientDepth() {
            MarketRules rules = new MarketRules(
                    TickSize.of("0.0025"), ShareQuantity.of("1"), false);
            OrderBookSnapshot shallow = new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH,
                    "hash", List.of(), List.of(level("0.5025", "1.23")), rules,
                    Optional.empty());

            ImmediatePlan.InsufficientDepth insufficient = assertInstanceOf(
                    ImmediatePlan.InsufficientDepth.class,
                    ImmediatePlanner.plan(ImmediateBuy.of(
                            ASSET, PusdAmount.of("1"), ExecutionPolicy.FOK), shallow));

            assertEquals(PusdAmount.of("0.618075"), insufficient.available());
        }
    }

    @Nested
    class SubGridDepth {

        private OrderBookSnapshot book(List<PriceLevel> bids, List<PriceLevel> asks) {
            return new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH, "hash", bids, asks,
                    new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false),
                    Optional.empty());
        }

        @Test
        void shouldTruncateBuyDepthWhenSharesAreOffGrid() {
            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateBuy.of(ASSET, PusdAmount.of("100"), ExecutionPolicy.FAK),
                            book(List.of(), List.of(level("0.50", "12.345")))));

            assertEquals(ShareQuantity.of("12.34"), plan.shares());
            assertEquals(Price.of("0.50"), plan.protectedPrice());
            assertEquals(PusdAmount.of("6.17"), plan.cost());
        }

        @Test
        void shouldKeepBuyProtectionWhenOffGridTailAddsNoSize() {
            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateBuy.of(ASSET, PusdAmount.of("100"), ExecutionPolicy.FAK),
                            book(List.of(), List.of(
                                    level("0.50", "12.345"), level("0.52", "0.001")))));

            assertEquals(Price.of("0.50"), plan.protectedPrice());
            assertEquals(ShareQuantity.of("12.34"), plan.shares());
            assertEquals(PusdAmount.of("6.17"), plan.cost());
        }

        @Test
        void shouldReportPartialSellWhenDepthIsOffGrid() {
            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateSell.of(ASSET, ShareQuantity.of("12.345"), ExecutionPolicy.FAK),
                            book(List.of(level("0.60", "12.345")), List.of())));

            assertEquals(ShareQuantity.of("12.34"), plan.shares());
            assertEquals(Price.of("0.60"), plan.protectedPrice());
            assertEquals(PusdAmount.of("7.404"), plan.cost());
            assertTrue(plan.partial(), "the 0.005 the grid discards was never sold");
        }

        @Test
        void shouldKeepSellProtectionWhenOffGridTailAddsNoSize() {
            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateSell.of(ASSET, ShareQuantity.of("12.346"), ExecutionPolicy.FAK),
                            book(List.of(level("0.60", "12.345"), level("0.55", "0.001")),
                                    List.of())));

            assertEquals(Price.of("0.60"), plan.protectedPrice());
            assertEquals(ShareQuantity.of("12.34"), plan.shares());
        }

        @Test
        void shouldReportInsufficientDepthWhenFokSizeIsOffGrid() {
            assertInstanceOf(ImmediatePlan.InsufficientDepth.class,
                    ImmediatePlanner.plan(
                            ImmediateSell.of(ASSET, ShareQuantity.of("12.345"), ExecutionPolicy.FOK),
                            book(List.of(level("0.60", "12.345")), List.of())));
        }

        @Test
        void shouldReportNoSharesWhenDepthIsBelowOneSizeStep() {
            OrderBookSnapshot dust = book(
                    List.of(level("0.60", "0.004")), List.of(level("0.50", "0.004")));

            ImmediatePlan.InsufficientDepth buy = assertInstanceOf(
                    ImmediatePlan.InsufficientDepth.class, ImmediatePlanner.plan(
                            ImmediateBuy.of(ASSET, PusdAmount.of("100"), ExecutionPolicy.FAK), dust));
            ImmediatePlan.InsufficientDepth sell = assertInstanceOf(
                    ImmediatePlan.InsufficientDepth.class, ImmediatePlanner.plan(
                            ImmediateSell.of(ASSET, ShareQuantity.of("10"), ExecutionPolicy.FAK),
                            dust));

            assertEquals(ShareQuantity.of("0"), buy.availableShares().orElseThrow());
            assertEquals(PusdAmount.of("0"), buy.available(),
                    "no shares were retained, so there is no notional to report");
            assertEquals(ShareQuantity.of("0"), sell.availableShares().orElseThrow());
            assertEquals(PusdAmount.of("0"), sell.available());
        }
    }

    @Nested
    class BookGuards {

        @Test
        void shouldThrowForDifferentAssetWhenPlanningImmediateIntent() {
            ImmediateBuy buy = ImmediateBuy.of(new TokenId("999"), PusdAmount.of("4"),
                    ExecutionPolicy.FAK);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ImmediatePlanner.plan(buy, book()));

            assertTrue(e.getMessage().contains("999") && e.getMessage().contains("12345"),
                    e.getMessage());
        }

        @Test
        void shouldThrowForBelowMinimumSellWhenPlanningImmediateIntent() {
            ImmediateSell sell = ImmediateSell.of(ASSET, ShareQuantity.of("4.999999"),
                    ExecutionPolicy.FAK);

            assertThrows(IllegalArgumentException.class, () -> ImmediatePlanner.plan(sell, book()));
        }

        @Test
        void shouldReportInsufficientDepthWhenBuyCannotReachMinimum() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("1"), ExecutionPolicy.FAK), book());

            ImmediatePlan.InsufficientDepth insufficient =
                    assertInstanceOf(ImmediatePlan.InsufficientDepth.class, plan);
            assertEquals(ShareQuantity.of("2"), insufficient.availableShares().orElseThrow());
        }
    }

    @Nested
    class Empty {

        @Test
        void shouldReportInsufficientDepthWhenBookSideIsEmpty() {
            OrderBookSnapshot empty = new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH, "hash",
                    List.of(), List.of(),
                    new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false),
                    Optional.of(Price.of("0.51")));

            assertInstanceOf(ImmediatePlan.InsufficientDepth.class, ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("10"), ExecutionPolicy.FAK), empty));
        }
    }
}
