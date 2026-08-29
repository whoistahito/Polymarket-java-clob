package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TokenId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trading: submission attributes derived from the Order Intent (issue #14)")
class OrderPlacementTest {

    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final TokenId ASSET = new TokenId("123");

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochSecond(1_799_000_000L), ZoneOffset.UTC);

    @Test
    @DisplayName("TC-OP-001: a Maker-Only Order Intent derives a post-only GTC placement")
    void makerOnlyIntentDerivesPostOnlyGtc() {
        OrderIntent intent = new MakerOnlyLimitOrder(
                ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));

        OrderPlacement placement = OrderPlacement.forIntent(CREDENTIALS, intent);

        assertEquals(OrderType.GTC, placement.orderType());
        assertTrue(placement.postOnly());
        assertEquals(0L, placement.expirationSeconds());
    }

    @Test
    @DisplayName("TC-OP-002: a plain Limit Order Intent derives a GTC placement that is not post-only")
    void limitIntentDerivesPlainGtc() {
        OrderIntent intent = new LimitOrder(
                ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));

        OrderPlacement placement = OrderPlacement.forIntent(CREDENTIALS, intent);

        assertEquals(OrderType.GTC, placement.orderType());
        assertTrue(!placement.postOnly());
    }

    @Test
    @DisplayName("TC-OP-003: a GTD Order Intent derives its wire expiration, threshold included")
    void goodTilDateIntentDerivesShiftedExpiration() {
        Instant expiresAt = Instant.ofEpochSecond(1_800_000_000L);
        OrderIntent intent = GoodTilDateOrder.expiringAt(
                ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"), expiresAt, CLOCK);

        OrderPlacement placement = OrderPlacement.forIntent(CREDENTIALS, intent);

        assertEquals(OrderType.GTD, placement.orderType());
        // constraints.json gtd.securityThresholdSeconds = 60: 1800000000 + 60.
        assertEquals(1_800_000_060L, placement.expirationSeconds());
        assertTrue(!placement.postOnly());
    }

    @Test
    @DisplayName("TC-OP-004: an immediate Order Intent derives its execution policy as the order type")
    void immediateIntentDerivesItsExecutionPolicy() {
        OrderPlacement fok = OrderPlacement.forIntent(CREDENTIALS,
                ImmediateBuy.of(ASSET, PusdAmount.of("5.2"), ExecutionPolicy.FOK));
        OrderPlacement fak = OrderPlacement.forIntent(CREDENTIALS,
                ImmediateSell.of(ASSET, ShareQuantity.of("10"), ExecutionPolicy.FAK));

        assertEquals(OrderType.FOK, fok.orderType());
        assertEquals(OrderType.FAK, fak.orderType());
    }

    @Test
    @DisplayName("TC-OP-005: a hand-built placement contradicting its Order Intent is refused")
    void contradictoryPlacementIsRefused() {
        OrderIntent makerOnly = new MakerOnlyLimitOrder(
                ASSET, Side.BUY, Price.of("0.52"), ShareQuantity.of("10"));
        OrderIntent gtd = GoodTilDateOrder.expiringAt(ASSET, Side.BUY, Price.of("0.52"),
                ShareQuantity.of("10"), Instant.ofEpochSecond(1_800_000_000L), CLOCK);

        assertThrows(IllegalArgumentException.class, () -> OrderPlacement
                .of(CREDENTIALS, OrderType.GTC).requireConsistentWith(makerOnly));
        assertThrows(IllegalArgumentException.class, () -> OrderPlacement
                .of(CREDENTIALS, OrderType.GTC).asPostOnly().requireConsistentWith(gtd));
        assertThrows(IllegalArgumentException.class, () -> OrderPlacement
                .goodTilDate(CREDENTIALS, 1_800_000_000L).requireConsistentWith(gtd));
    }

    @Test
    @DisplayName("TC-OP-006: a placement derived from an Order Intent is consistent with it")
    void derivedPlacementIsConsistent() {
        OrderIntent gtd = GoodTilDateOrder.expiringAt(ASSET, Side.BUY, Price.of("0.52"),
                ShareQuantity.of("10"), Instant.ofEpochSecond(1_800_000_000L), CLOCK);

        OrderPlacement.forIntent(CREDENTIALS, gtd).requireConsistentWith(gtd);
    }
}
