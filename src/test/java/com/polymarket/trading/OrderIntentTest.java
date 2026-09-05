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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderIntentTest {

    private static final TokenId ASSET = new TokenId("12345");
    private static final Instant NOW = Instant.ofEpochSecond(1773890758L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    class Limits {

        @Test
        void shouldNotPromiseMakerExecutionWhenLimitIsOrdinary() {
            LimitOrder order = new LimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));
            assertFalse(order.postOnly(), "an ordinary limit may cross and take liquidity");
        }

        @Test
        void shouldMapToPostOnlyWhenLimitIsMakerOnly() {
            MakerOnlyLimitOrder order =
                    new MakerOnlyLimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));
            assertTrue(order.postOnly(), "the exchange must reject it if it would take");
        }

        @Test
        void shouldThrowForNonPositiveSizeWhenCreatingLimitOrder() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LimitOrder(ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("0")));
        }

        @Test
        void shouldUseOfficialWireValuesWhenEncodingSide() {
            assertEquals(0, Side.BUY.wireValue());
            assertEquals(1, Side.SELL.wireValue());
        }
    }

    @Nested
    class GoodTilDate {

        @Test
        void shouldThrowForTooSoonExpirationWhenCreatingGoodTilDateOrder() {
            Instant tooSoon = NOW.plus(Duration.ofSeconds(119));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> GoodTilDateOrder.expiringAt(
                            ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), tooSoon, CLOCK));
            assertTrue(e.getMessage().contains("120"), e.getMessage());
        }

        @Test
        void shouldAcceptMinimumExpirationWhenWireThresholdIsApplied() {
            // The wire value adds 60 seconds, so an effective expiry 120 seconds out is stated
            // exactly 180 seconds in the future as the official rule requires.
            Instant exactly = NOW.plus(Duration.ofSeconds(120));
            GoodTilDateOrder order = GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), exactly, CLOCK);
            assertEquals(exactly, order.expiresAt());
            assertEquals(NOW.plusSeconds(180).getEpochSecond(), order.expirationSeconds());
        }

        @Test
        void shouldAddSecurityThresholdWhenEncodingExpiration() {
            // Official: an order expires one minute BEFORE its stated expiration, so to be live
            // until T the wire value must be T + 60.
            Instant wanted = NOW.plus(Duration.ofHours(1));
            GoodTilDateOrder order = GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), wanted, CLOCK);

            assertEquals(wanted.getEpochSecond() + 60, order.expirationSeconds());
        }

        @Test
        void shouldThrowForInvalidLifetimeWhenAllConstructionPathsAreChecked() {
            for (Constructor<?> constructor : GoodTilDateOrder.class.getConstructors()) {
                fail("GoodTilDateOrder exposes a constructor that bypasses the lifetime check: "
                        + constructor);
            }
            assertThrows(IllegalArgumentException.class, () -> GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                    NOW.plusSeconds(1), CLOCK));
        }

        @Test
        void shouldThrowForPastExpirationWhenCreatingGoodTilDateOrder() {
            assertThrows(IllegalArgumentException.class, () -> GoodTilDateOrder.expiringAt(
                    ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                    NOW.minusSeconds(1), CLOCK));
        }
    }

    @Nested
    class Immediate {

        @Test
        void shouldDenominateBuyInPusdWhenCreatingImmediateIntent() {
            ImmediateBuy buy = ImmediateBuy.of(ASSET, PusdAmount.of("10"), ExecutionPolicy.FOK);
            assertEquals(PusdAmount.of("10"), buy.budget());
            assertEquals(Side.BUY, buy.side());
        }

        @Test
        void shouldDenominateSellInSharesWhenCreatingImmediateIntent() {
            ImmediateSell sell = ImmediateSell.of(ASSET, ShareQuantity.of("10"), ExecutionPolicy.FAK);
            assertEquals(ShareQuantity.of("10"), sell.size());
            assertEquals(Side.SELL, sell.side());
        }

        @Test
        void shouldThrowForZeroAmountWhenCreatingImmediateIntent() {
            assertThrows(IllegalArgumentException.class,
                    () -> ImmediateBuy.of(ASSET, PusdAmount.of("0"), ExecutionPolicy.FOK));
            assertThrows(IllegalArgumentException.class,
                    () -> ImmediateSell.of(ASSET, ShareQuantity.of("0"), ExecutionPolicy.FOK));
        }

        @Test
        void shouldKeepCallerBoundaryOptionalWhenImmediateIntentIsUnbounded() {
            ImmediateBuy plain = ImmediateBuy.of(ASSET, PusdAmount.of("10"), ExecutionPolicy.FOK);
            assertTrue(plain.maximumPrice().isEmpty());

            ImmediateBuy bounded = plain.notAbove(Price.of("0.60"));
            assertEquals(Price.of("0.60"), bounded.maximumPrice().orElseThrow());
            assertTrue(plain.maximumPrice().isEmpty(), "the original intent is unchanged");
        }
    }
}
