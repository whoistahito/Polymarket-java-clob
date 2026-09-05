package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.PriceLevel;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderExecutionTest {

    private static final TokenId ASSET = new TokenId("12345");
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false);
    private static final ApiCredentials CREDENTIALS =
            new ApiCredentials("key", "c2VjcmV0", "passphrase");
    private static final Instant NOW = Instant.ofEpochSecond(1773890758L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    class Limits {

        @Test
        void shouldApplyPostOnlyWhenIntentIsMakerOnly() {
            OrderExecution plain = OrderExecution.of(
                    new LimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10")), RULES);
            OrderExecution makerOnly = OrderExecution.of(
                    new MakerOnlyLimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10")),
                    RULES);

            assertFalse(plain.placement(CREDENTIALS).postOnly(),
                    "an ordinary limit may cross and take liquidity");
            assertTrue(makerOnly.placement(CREDENTIALS).postOnly(),
                    "the exchange must reject a maker-only order that would take");
            assertEquals(OrderType.GTC, plain.placement(CREDENTIALS).orderType());
            assertEquals(OrderType.GTC, makerOnly.placement(CREDENTIALS).orderType());
        }
    }

    @Nested
    class GoodTilDate {

        @Test
        void shouldPreserveValidatedExpirationWhenIntentIsGoodTilDate() {
            Instant wanted = NOW.plus(Duration.ofHours(1));
            GoodTilDateOrder intent = GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), wanted, CLOCK);

            OrderPlacement placement = OrderExecution.of(intent, RULES).placement(CREDENTIALS);

            assertEquals(OrderType.GTD, placement.orderType());
            assertEquals(wanted.getEpochSecond() + 60, placement.expirationSeconds());
        }

        @Test
        void shouldThrowForInvalidPlacementWhenTypeAndLifetimeContradict() {
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderPlacement(CREDENTIALS, OrderType.GTD, 0L, false));
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderPlacement(CREDENTIALS, OrderType.GTC, 1_800_000_000L, false));
            assertThrows(IllegalArgumentException.class,
                    () -> OrderPlacement.of(CREDENTIALS, OrderType.FOK).asPostOnly());
            assertThrows(IllegalArgumentException.class,
                    () -> OrderPlacement.of(CREDENTIALS, OrderType.FAK).asPostOnly());
        }
    }

    @Nested
    class Immediate {

        private OrderBookSnapshot book() {
            return new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH, "hash",
                    List.of(new PriceLevel(Price.of("0.48"), ShareQuantity.of("20")),
                            new PriceLevel(Price.of("0.50"), ShareQuantity.of("30"))),
                    List.of(new PriceLevel(Price.of("0.50"), ShareQuantity.of("10")),
                            new PriceLevel(Price.of("0.52"), ShareQuantity.of("20"))),
                    RULES, Optional.of(Price.of("0.51")));
        }

        @Test
        void shouldPriceBuyAtProtectedLevelWhenImmediateIntentCrossesDepth() {
            ImmediateBuy intent = ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK);
            ImmediatePlan.Executable plan = (ImmediatePlan.Executable)
                    ImmediatePlanner.plan(intent, book());

            OrderExecution execution = OrderExecution.of(intent, plan, RULES);

            assertEquals(Price.of("0.52"), execution.price());
            assertEquals(ShareQuantity.of("21.15"), execution.shares());
            // The signed leg is repriced at 0.52, so a blended walk would exceed the 11.00 budget.
            assertEquals(PusdAmount.of("10.998"), execution.pusdLeg());
            assertTrue(execution.pusdLeg().value().compareTo(intent.budget().value()) <= 0,
                    "the signed leg may never authorise more than the budget");
            assertEquals(OrderType.FAK, execution.orderType());
        }

        @Test
        void shouldPriceSellAtProtectedLevelWhenImmediateIntentCrossesDepth() {
            ImmediateSell intent = ImmediateSell.of(ASSET, ShareQuantity.of("40"),
                    ExecutionPolicy.FOK);
            ImmediatePlan.Executable plan = (ImmediatePlan.Executable)
                    ImmediatePlanner.plan(intent, book());

            OrderExecution execution = OrderExecution.of(intent, plan, RULES);

            assertEquals(Price.of("0.48"), execution.price());
            // The signed floor is 40 x 0.48; a blended value is not guaranteed by the order.
            assertEquals(PusdAmount.of("19.2"), plan.cost());
            assertEquals(PusdAmount.of("19.2"), execution.pusdLeg());
            assertEquals(OrderType.FOK, execution.orderType());
        }

        @Test
        void shouldThrowWhenHandBuiltPlanExceedsImmediateIntent() {
            ImmediatePlan.Executable oversizedBuy = new ImmediatePlan.Executable(
                    Price.of("0.50"), ShareQuantity.of("10"), PusdAmount.of("5"),
                    PusdAmount.of("0"), false);
            ImmediatePlan.Executable partialFokSell = new ImmediatePlan.Executable(
                    Price.of("0.50"), ShareQuantity.of("5"), PusdAmount.of("2.5"),
                    PusdAmount.of("0"), true);

            assertThrows(IllegalArgumentException.class, () -> OrderExecution.of(
                    ImmediateBuy.of(ASSET, PusdAmount.of("1"), ExecutionPolicy.FAK),
                    oversizedBuy, RULES));
            assertThrows(IllegalArgumentException.class, () -> OrderExecution.of(
                    ImmediateSell.of(ASSET, ShareQuantity.of("10"), ExecutionPolicy.FOK),
                    partialFokSell, RULES));
        }

        private OrderBookSnapshot fineGrainedBook() {
            return new OrderBookSnapshot("0xcond", ASSET, Instant.EPOCH, "hash",
                    List.of(new PriceLevel(Price.of("0.50"), ShareQuantity.of("12.345"))),
                    List.of(new PriceLevel(Price.of("0.50"), ShareQuantity.of("12.345"))),
                    RULES, Optional.empty());
        }

        @Test
        void shouldKeepBuyLegOnSizeGridWhenDepthHasFinePrecision() {
            ImmediateBuy intent = ImmediateBuy.of(ASSET, PusdAmount.of("100"), ExecutionPolicy.FAK);
            ImmediatePlan.Executable plan = (ImmediatePlan.Executable)
                    ImmediatePlanner.plan(intent, fineGrainedBook());

            OrderExecution execution = OrderExecution.of(intent, plan, RULES);

            assertEquals(Price.of("0.50"), execution.price());
            assertEquals(ShareQuantity.of("12.34"), execution.shares());
            assertEquals(PusdAmount.of("6.17"), execution.pusdLeg());
        }

        @Test
        void shouldAcceptPartialSellWhenDepthHasFinePrecision() {
            ImmediateSell intent = ImmediateSell.of(ASSET, ShareQuantity.of("12.345"),
                    ExecutionPolicy.FAK);
            ImmediatePlan.Executable plan = (ImmediatePlan.Executable)
                    ImmediatePlanner.plan(intent, fineGrainedBook());

            OrderExecution execution = OrderExecution.of(intent, plan, RULES);

            assertTrue(plan.partial(), "the 0.005 the size grid discards is not sold");
            assertEquals(ShareQuantity.of("12.34"), execution.shares());
            assertEquals(PusdAmount.of("6.17"), execution.pusdLeg());
        }
    }
}
