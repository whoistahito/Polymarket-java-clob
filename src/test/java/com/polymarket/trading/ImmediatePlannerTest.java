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
            // Every share is repriced at 0.52, so 11.00 pUSD carries 21.15 of them — not the
            // 21.538461 a blended walk would claim and then be unable to pay for.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.52"), executable.protectedPrice());
            assertEquals(ShareQuantity.of("21.15"), executable.shares());
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
            assertEquals(ShareQuantity.of("60"), insufficient.availableShares().orElseThrow(),
                    "an all-or-nothing shortfall still reports what the book could have filled");
        }

        @Test
        @DisplayName("TC-DP-005: FAK fills what is there and reports the shortfall")
        void fakFillsWhatIsAvailable() {
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("50"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.54"), executable.protectedPrice());
            // The book is worth 31.60 at its own prices, but the order authorises all 60 shares
            // at the 0.54 it is signed at.
            assertEquals(PusdAmount.of("32.4"), executable.cost());
            assertTrue(executable.partial(), "a FAK that could not spend the budget is partial");
        }

        @Test
        @DisplayName("TC-DP-006: a fee-aware budget is spent on the nonlinear official fee")
        void feeAwareBudgetReservesTheFee() {
            // Crypto taker rate 0.07. At 0.50 the fee is 0.07 x 0.50 x 0.50 = 0.0175 per share,
            // so 5.175 pUSD buys exactly the ten shares resting at 0.50 and nothing more.
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
        @DisplayName("TC-DP-007a: the same budget without a fee rate buys strictly more")
        void aFeeFreeBudgetBuysMore() {
            // Crossing to 0.52 would reprice all ten shares there and buy only 9.95, so the whole
            // 0.50 level is the best 5.175 can do — and without a fee it keeps every share of it.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("5.175"), ExecutionPolicy.FAK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(ShareQuantity.of("10"), executable.shares());
            assertEquals(Price.of("0.50"), executable.protectedPrice());
            assertEquals(PusdAmount.of("0"), executable.fee());
        }

        @Test
        @DisplayName("TC-DP-015: the pUSD leg an immediate BUY authorises never exceeds its budget")
        void theAuthorisedSpendStaysInsideTheBudget() {
            // 10 shares rest at 0.50 and 20 more at 0.52. A blended walk would buy every share the
            // budget covers at book prices and then reprice all of them at 0.52, spending more than
            // the caller allowed. The plan must be affordable at the price it is actually signed at.
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
        @DisplayName("TC-DP-016: a fee-aware budget also bounds the repriced leg")
        void theAuthorisedSpendStaysInsideAFeeAwareBudget() {
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
        @DisplayName("TC-DP-017: planned shares carry the tick profile's size precision, not six decimals")
        void plannedSharesFollowTheDocumentedSizePrecision() {
            // The official "Choose a Price and Size" table gives every documented tick two size
            // decimals; a six-decimal share count is not a size the exchange accepts.
            ImmediatePlan.Executable plan = assertInstanceOf(ImmediatePlan.Executable.class,
                    ImmediatePlanner.plan(
                            ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK), book()));

            assertTrue(plan.shares().value().stripTrailingZeros().scale()
                            <= book().rules().tickSize().sizeDecimals(),
                    "planned " + plan.shares() + " carries more decimals than the tick allows");
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
            // 40 shares reach down to 0.48, and the order is signed at that floor: 40 x 0.48.
            // The walk is worth 19.80 at its own level prices, but 19.20 is what it guarantees.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateSell.of(ASSET, ShareQuantity.of("40"), ExecutionPolicy.FOK), book());

            ImmediatePlan.Executable executable = assertInstanceOf(ImmediatePlan.Executable.class, plan);
            assertEquals(Price.of("0.48"), executable.protectedPrice());
            assertEquals(PusdAmount.of("19.2"), executable.cost());
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

        @Test
        @DisplayName("TC-DP-018: reported pUSD depth keeps the tick profile's amount precision")
        void insufficientDepthKeepsAmountPrecision() {
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
    @DisplayName("book identity and minimum")
    class BookGuards {

        @Test
        @DisplayName("TC-DP-012: a book for a different asset cannot plan this intent")
        void rejectsABookForAnotherAsset() {
            ImmediateBuy buy = ImmediateBuy.of(new TokenId("999"), PusdAmount.of("4"),
                    ExecutionPolicy.FAK);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ImmediatePlanner.plan(buy, book()));

            assertTrue(e.getMessage().contains("999") && e.getMessage().contains("12345"),
                    e.getMessage());
        }

        @Test
        @DisplayName("TC-DP-013: a SELL below the book minimum is rejected, never planned")
        void rejectsASellBelowTheBookMinimum() {
            // The book publishes min_order_size 5 shares.
            ImmediateSell sell = ImmediateSell.of(ASSET, ShareQuantity.of("4.999999"),
                    ExecutionPolicy.FAK);

            assertThrows(IllegalArgumentException.class, () -> ImmediatePlanner.plan(sell, book()));
        }

        @Test
        @DisplayName("TC-DP-014: a walk that cannot reach the book minimum is insufficient depth")
        void aSubMinimumWalkIsInsufficientDepth() {
            // 1.00 pUSD at 0.50 buys 2 shares, under the book's 5-share minimum.
            ImmediatePlan plan = ImmediatePlanner.plan(
                    ImmediateBuy.of(ASSET, PusdAmount.of("1"), ExecutionPolicy.FAK), book());

            ImmediatePlan.InsufficientDepth insufficient =
                    assertInstanceOf(ImmediatePlan.InsufficientDepth.class, plan);
            assertEquals(ShareQuantity.of("2"), insufficient.availableShares().orElseThrow());
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
