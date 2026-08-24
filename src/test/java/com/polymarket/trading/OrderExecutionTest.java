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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Order Intents carried through to the wire (issue #11)")
class OrderExecutionTest {

    private static final TokenId ASSET = new TokenId("12345");
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("5"), false);
    private static final ApiCredentials CREDENTIALS =
            new ApiCredentials("key", "c2VjcmV0", "passphrase");
    private static final Instant NOW = Instant.ofEpochSecond(1773890758L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("limit intents")
    class Limits {

        @Test
        @DisplayName("TC-OE-001: Maker-Only reaches the wire as post-only and a plain limit does not")
        void makerOnlyIsTheOnlyPostOnlyLimit() {
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
    @DisplayName("good-til-date")
    class GoodTilDate {

        @Test
        @DisplayName("TC-OE-002: GTD carries its validated expiration all the way to the wire")
        void gtdKeepsItsValidatedExpiration() {
            Instant wanted = NOW.plus(Duration.ofHours(1));
            GoodTilDateOrder intent = GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), wanted, CLOCK);

            OrderPlacement placement = OrderExecution.of(intent, RULES).placement(CREDENTIALS);

            assertEquals(OrderType.GTD, placement.orderType());
            assertEquals(wanted.getEpochSecond() + 60, placement.expirationSeconds());
        }

        @Test
        @DisplayName("TC-OE-003: invalid order-type and lifetime combinations cannot reach the wire")
        void invalidCombinationsAreRefused() {
            // GTD without an expiration would rest forever; GTC with one is a contradiction.
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderPlacement(CREDENTIALS, OrderType.GTD, 0L, false));
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderPlacement(CREDENTIALS, OrderType.GTC, 1_800_000_000L, false));
            // An immediate order never rests, so it cannot be post-only.
            assertThrows(IllegalArgumentException.class,
                    () -> OrderPlacement.of(CREDENTIALS, OrderType.FOK).asPostOnly());
            assertThrows(IllegalArgumentException.class,
                    () -> OrderPlacement.of(CREDENTIALS, OrderType.FAK).asPostOnly());
        }
    }

    @Nested
    @DisplayName("immediate intents")
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
        @DisplayName("TC-OE-004: an immediate BUY leg pays the Protected Price, not the blended cost")
        void immediateBuyLegUsesTheProtectedPrice() {
            ImmediateBuy intent = ImmediateBuy.of(ASSET, PusdAmount.of("11"), ExecutionPolicy.FAK);
            ImmediatePlan.Executable plan = (ImmediatePlan.Executable)
                    ImmediatePlanner.plan(intent, book());

            OrderExecution execution = OrderExecution.of(intent, plan, RULES);

            assertEquals(Price.of("0.52"), execution.price());
            assertEquals(ShareQuantity.of("21.538461"), execution.shares());
            // The walk blends 0.50 and 0.52 into an 11.00 spend; the signed leg must clear the
            // whole size at 0.52, which is 11.19999972 truncated to the grid's four decimals.
            assertEquals(PusdAmount.of("11.1999"), execution.pusdLeg());
            assertEquals(OrderType.FAK, execution.orderType());
        }

        @Test
        @DisplayName("TC-OE-005: an immediate SELL leg receives the Protected Price, not the blended proceeds")
        void immediateSellLegUsesTheProtectedPrice() {
            ImmediateSell intent = ImmediateSell.of(ASSET, ShareQuantity.of("40"),
                    ExecutionPolicy.FOK);
            ImmediatePlan.Executable plan = (ImmediatePlan.Executable)
                    ImmediatePlanner.plan(intent, book());

            OrderExecution execution = OrderExecution.of(intent, plan, RULES);

            assertEquals(Price.of("0.48"), execution.price());
            // 30 at 0.50 plus 10 at 0.48 blends to 19.80; the signed floor is 40 x 0.48.
            assertEquals(PusdAmount.of("19.8"), plan.cost());
            assertEquals(PusdAmount.of("19.2"), execution.pusdLeg());
            assertEquals(OrderType.FOK, execution.orderType());
        }
    }
}
