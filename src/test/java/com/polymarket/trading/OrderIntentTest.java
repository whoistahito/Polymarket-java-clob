package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TokenId;
import java.time.Clock;
import java.time.Duration;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Trading intents")
class OrderIntentTest {

    private static final TokenId ASSET = new TokenId("12345");
    private static final Instant NOW = Instant.ofEpochSecond(1773890758L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("limit intents")
    class Limits {

        @Test
        @DisplayName("TC-TI-001: an ordinary limit order does not promise maker execution")
        void ordinaryLimitDoesNotPromiseMaker() {
            LimitOrder order = new LimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));
            assertFalse(order.postOnly(), "an ordinary limit may cross and take liquidity");
        }

        @Test
        @DisplayName("TC-TI-002: a maker-only limit order maps to postOnly")
        void makerOnlyMapsToPostOnly() {
            MakerOnlyLimitOrder order =
                    new MakerOnlyLimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));
            assertTrue(order.postOnly(), "the exchange must reject it if it would take");
        }

        @Test
        @DisplayName("TC-TI-003: a limit intent needs a positive size")
        void limitNeedsPositiveSize() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("0")));
        }

        @Test
        @DisplayName("TC-TI-004: side encodes to the official wire values")
        void sideUsesOfficialEncoding() {
            assertEquals(0, Side.BUY.wireValue());
            assertEquals(1, Side.SELL.wireValue());
        }
    }

    @Nested
    @DisplayName("good-til-date")
    class GoodTilDate {

        @Test
        @DisplayName("TC-TI-005: an expiration under the official three-minute minimum is rejected")
        void rejectsTooSoonExpiration() {
            Instant tooSoon = NOW.plus(Duration.ofSeconds(179));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> GoodTilDateOrder.expiringAt(
                            ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), tooSoon, CLOCK));
            assertTrue(e.getMessage().contains("180"), e.getMessage());
        }

        @Test
        @DisplayName("TC-TI-006: the official minimum itself is accepted")
        void acceptsTheMinimum() {
            Instant exactly = NOW.plus(Duration.ofSeconds(180));
            GoodTilDateOrder order = GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), exactly, CLOCK);
            assertEquals(exactly, order.expiresAt());
        }

        @Test
        @DisplayName("TC-TI-007: the wire expiration carries the documented one-minute buffer")
        void wireExpirationAddsTheSecurityThreshold() {
            // Official: an order expires one minute BEFORE its stated expiration, so to be live
            // until T the wire value must be T + 60.
            Instant wanted = NOW.plus(Duration.ofHours(1));
            GoodTilDateOrder order = GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), wanted, CLOCK);

            assertEquals(wanted.getEpochSecond() + 60, order.expirationSeconds());
        }

        @Test
        @DisplayName("TC-TI-011: there is no construction path that skips the lifetime check")
        void everyConstructionPathValidatesTheLifetime() {
            for (Constructor<?> constructor : GoodTilDateOrder.class.getConstructors()) {
                fail("GoodTilDateOrder exposes a constructor that bypasses the lifetime check: "
                        + constructor);
            }
            assertThrows(IllegalArgumentException.class, () -> GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                    NOW.plusSeconds(1), CLOCK));
        }

        @Test
        @DisplayName("TC-TI-008: an expiration in the past is rejected against the injected clock")
        void rejectsPastExpiration() {
            assertThrows(IllegalArgumentException.class, () -> GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                    NOW.minusSeconds(1), CLOCK));
        }
    }

    @Nested
    @DisplayName("immediate intents")
    class Immediate {

        @Test
        @DisplayName("TC-TI-009: an immediate BUY is denominated in pUSD")
        void immediateBuySpendsPusd() {
            ImmediateBuy buy = ImmediateBuy.of(ASSET, PusdAmount.of("10"), ExecutionPolicy.FOK);
            assertEquals(PusdAmount.of("10"), buy.budget());
            assertEquals(Side.BUY, buy.side());
        }

        @Test
        @DisplayName("TC-TI-010: an immediate SELL is denominated in shares")
        void immediateSellSpendsShares() {
            ImmediateSell sell = ImmediateSell.of(ASSET, ShareQuantity.of("10"), ExecutionPolicy.FAK);
            assertEquals(ShareQuantity.of("10"), sell.size());
            assertEquals(Side.SELL, sell.side());
        }

        @Test
        @DisplayName("TC-TI-011: a zero budget or size cannot form an immediate intent")
        void immediateIntentsNeedSomethingToSpend() {
            assertThrows(IllegalArgumentException.class,
                    () -> ImmediateBuy.of(ASSET, PusdAmount.of("0"), ExecutionPolicy.FOK));
            assertThrows(IllegalArgumentException.class,
                    () -> ImmediateSell.of(ASSET, ShareQuantity.of("0"), ExecutionPolicy.FOK));
        }

        @Test
        @DisplayName("TC-TI-012: a caller boundary is optional and absent by default")
        void callerBoundaryIsOptional() {
            ImmediateBuy plain = ImmediateBuy.of(ASSET, PusdAmount.of("10"), ExecutionPolicy.FOK);
            assertTrue(plain.maximumPrice().isEmpty());

            ImmediateBuy bounded = plain.notAbove(Price.of("0.60"));
            assertEquals(Price.of("0.60"), bounded.maximumPrice().orElseThrow());
            assertTrue(plain.maximumPrice().isEmpty(), "the original intent is unchanged");
        }
    }
}
