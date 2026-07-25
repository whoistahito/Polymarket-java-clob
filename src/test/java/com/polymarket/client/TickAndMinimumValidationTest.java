package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.CreateOrderOptions;
import com.polymarket.model.OrderType;
import com.polymarket.model.SignatureType;
import com.polymarket.model.Side;
import com.polymarket.model.SignedOrder;
import com.polymarket.model.UserMarketOrder;
import com.polymarket.model.UserOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

/**
 * Ticket 023 — tick support and minimum-size validation after normalization.
 *
 * <p>Two real hazards are pinned here. First, {@code OrderBuilder} used to fall back to the
 * {@code 0.01} rounding profile for any tick it did not recognise, which silently mis-rounds every
 * order on the official {@code 0.005} and {@code 0.0025} markets. Second, the minimum-size check ran
 * against the caller's raw size rather than the truncated quantity that actually gets signed, so a
 * {@code 10.009}-share order could be signed as {@code 10.00} against a {@code 10.005} minimum.
 */
@DisplayName("TC-TMV — tick and minimum validation (Ticket 023)")
class TickAndMinimumValidationTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String TOKEN_ID =
        "71321045679252212594626385532706912750332728571942532289631379312455583992563";

    private static final BigDecimal TOKEN_UNITS = new BigDecimal("1000000");

    private static OrderBuilder builder() {
        return new OrderBuilder(Credentials.create(PK), 137, SignatureType.EOA, null);
    }

    private static CreateOrderOptions opts(String tick) {
        return CreateOrderOptions.builder().tickSize(tick).negRisk(false).build();
    }

    private static CreateOrderOptions opts(String tick, String minSize) {
        return CreateOrderOptions.builder()
            .tickSize(tick)
            .negRisk(false)
            .orderMinSize(new BigDecimal(minSize))
            .build();
    }

    private static SignedOrder limit(String tick, Side side, String price, String size) {
        return builder().buildOrder(
            UserOrder.builder()
                .tokenID(TOKEN_ID).side(side)
                .price(new BigDecimal(price)).size(new BigDecimal(size)).feeRateBps(0).build(),
            opts(tick),
            OrderType.GTC);
    }

    /** Share quantity actually carried by a signed order: taker side for a BUY, maker side for a SELL. */
    private static BigDecimal signedShares(SignedOrder order) {
        String raw = order.side() == Side.BUY ? order.takerAmount() : order.makerAmount();
        return new BigDecimal(raw).divide(TOKEN_UNITS);
    }

    // ------------------------------------------------------------------ //
    // Documented ticks: 0.005 and 0.0025                                  //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-TMV-001 limit BUY and SELL sign correctly at tick 0.005")
    void limitOrdersAtHalfCentTick() {
        SignedOrder buy = limit("0.005", Side.BUY, "0.455", "10");
        // 10 shares at 0.455 = 4.55 USDC. A 0.01 fallback profile would have rounded the price to
        // 0.46 and paid 4.60.
        assertEquals("4550000", buy.makerAmount());
        assertEquals("10000000", buy.takerAmount());

        SignedOrder sell = limit("0.005", Side.SELL, "0.455", "10");
        assertEquals("10000000", sell.makerAmount());
        assertEquals("4550000", sell.takerAmount());
    }

    @Test
    @DisplayName("TC-TMV-002 limit BUY and SELL sign correctly at tick 0.0025")
    void limitOrdersAtQuarterCentTick() {
        SignedOrder buy = limit("0.0025", Side.BUY, "0.4525", "20");
        assertEquals("9050000", buy.makerAmount());
        assertEquals("20000000", buy.takerAmount());

        SignedOrder sell = limit("0.0025", Side.SELL, "0.4525", "20");
        assertEquals("20000000", sell.makerAmount());
        assertEquals("9050000", sell.takerAmount());
    }

    @Test
    @DisplayName("TC-TMV-003 a price already on the 0.0025 grid is not moved by rounding")
    void quarterCentPricePreserved() {
        // 0.4525 is a valid multiple of 0.0025; the 0.01 fallback would snap it to 0.45.
        assertEquals("9050000", limit("0.0025", Side.BUY, "0.4525", "20").makerAmount());
        assertEquals("9150000", limit("0.0025", Side.BUY, "0.4575", "20").makerAmount());
    }

    @Test
    @DisplayName("TC-TMV-004 market BUY and SELL work at ticks 0.005 and 0.0025")
    void marketOrdersAtFineTicks() {
        for (String tick : new String[] {"0.005", "0.0025"}) {
            SignedOrder buy = builder().buildMarketOrder(
                UserMarketOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.BUY)
                    .amount(new BigDecimal("10")).price(new BigDecimal("0.5")).build(),
                opts(tick));
            assertNotNull(buy.signature(), "market BUY must sign at tick " + tick);
            assertEquals("10000000", buy.makerAmount(), "BUY maker is the USDC amount");
            assertTrue(new BigDecimal(buy.takerAmount()).signum() > 0,
                "market BUY at tick " + tick + " must not derive zero proceeds");

            SignedOrder sell = builder().buildMarketOrder(
                UserMarketOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.SELL)
                    .amount(new BigDecimal("10")).price(new BigDecimal("0.5")).build(),
                opts(tick));
            assertEquals("10000000", sell.makerAmount(), "SELL maker is the share amount");
            assertTrue(new BigDecimal(sell.takerAmount()).signum() > 0,
                "market SELL at tick " + tick + " must not derive zero proceeds");
        }
    }

    @Test
    @DisplayName("TC-TMV-005 every documented tick keeps its own rounding profile")
    void allDocumentedTicksSupported() {
        for (String tick : new String[] {"0.1", "0.01", "0.005", "0.0025", "0.001", "0.0001"}) {
            assertDoesNotThrow(() -> limit(tick, Side.BUY, "0.5", "10"), "tick " + tick);
        }
    }

    @Test
    @DisplayName("TC-TMV-006 an equivalent tick spelling resolves to the same profile")
    void equivalentTickSpellingAccepted() {
        assertEquals(
            limit("0.01", Side.BUY, "0.45", "10").makerAmount(),
            limit("0.010", Side.BUY, "0.45", "10").makerAmount());
    }

    // ------------------------------------------------------------------ //
    // Unknown ticks are rejected, never silently defaulted                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-TMV-007 an unknown tick fails before signing on the limit path")
    void unknownTickRejectedForLimitOrders() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> limit("0.02", Side.BUY, "0.5", "10"));
        assertTrue(ex.getMessage().contains("tick"), ex.getMessage());
    }

    @Test
    @DisplayName("TC-TMV-008 an unknown tick fails before signing on the market path")
    void unknownTickRejectedForMarketOrders() {
        assertThrows(IllegalArgumentException.class,
            () -> builder().buildMarketOrder(
                UserMarketOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.SELL)
                    .amount(new BigDecimal("10")).price(new BigDecimal("0.5")).build(),
                opts("0.02")));
    }

    @Test
    @DisplayName("TC-TMV-009 a malformed, null, or non-positive tick is rejected")
    void malformedTickRejected() {
        assertThrows(IllegalArgumentException.class, () -> limit("abc", Side.BUY, "0.5", "10"));
        assertThrows(IllegalArgumentException.class, () -> limit("0", Side.BUY, "0.5", "10"));
        assertThrows(IllegalArgumentException.class, () -> limit("-0.01", Side.BUY, "0.5", "10"));
        assertThrows(IllegalArgumentException.class, () -> builder().buildOrder(
            UserOrder.builder()
                .tokenID(TOKEN_ID).side(Side.BUY)
                .price(new BigDecimal("0.5")).size(BigDecimal.TEN).feeRateBps(0).build(),
            opts(null),
            OrderType.GTC));
    }

    // ------------------------------------------------------------------ //
    // Minimum size compared against the NORMALIZED quantity               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-TMV-010 size 10.009 against minimum 10.005 rejects — it would sign as 10.00")
    void truncatedSizeBelowMinimumRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> builder().buildOrder(
                UserOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.BUY)
                    .price(new BigDecimal("0.5")).size(new BigDecimal("10.009")).feeRateBps(0).build(),
                opts("0.01", "10.005"),
                OrderType.GTC));
        assertTrue(ex.getMessage().contains("minimum order size"), ex.getMessage());
        assertTrue(ex.getMessage().contains("10.00"), ex.getMessage());
    }

    @Test
    @DisplayName("TC-TMV-011 a representable 10.01 meets the same 10.005 minimum")
    void representableSizeAboveMinimumPasses() {
        SignedOrder signed = builder().buildOrder(
            UserOrder.builder()
                .tokenID(TOKEN_ID).side(Side.BUY)
                .price(new BigDecimal("0.5")).size(new BigDecimal("10.01")).feeRateBps(0).build(),
            opts("0.01", "10.005"),
            OrderType.GTC);

        assertEquals(0, new BigDecimal("10.01").compareTo(signedShares(signed)));
    }

    @Test
    @DisplayName("TC-TMV-012 the minimum applies to a limit SELL too")
    void limitSellBelowMinimumRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> builder().buildOrder(
                UserOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.SELL)
                    .price(new BigDecimal("0.5")).size(new BigDecimal("4.999")).feeRateBps(0).build(),
                opts("0.01", "5"),
                OrderType.GTC));
    }

    @Test
    @DisplayName("TC-TMV-013 a sub-minimum market SELL is rejected — amount is the share quantity")
    void marketSellBelowMinimumRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> builder().buildMarketOrder(
                UserMarketOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.SELL)
                    .amount(new BigDecimal("4.999")).price(new BigDecimal("0.5")).build(),
                opts("0.01", "5")));
        assertTrue(ex.getMessage().contains("minimum order size"), ex.getMessage());
    }

    @Test
    @DisplayName("TC-TMV-014 a market BUY whose spend buys fewer than the minimum shares is rejected")
    void marketBuyBelowMinimumRejected() {
        // $2 at 0.50 buys 4 shares, under a 5-share minimum, even though the amount reads as "2".
        assertThrows(IllegalArgumentException.class,
            () -> builder().buildMarketOrder(
                UserMarketOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.BUY)
                    .amount(new BigDecimal("2")).price(new BigDecimal("0.5")).build(),
                opts("0.01", "5")));
    }

    @Test
    @DisplayName("TC-TMV-015 a market BUY that clears the minimum still signs")
    void marketBuyAboveMinimumPasses() {
        SignedOrder signed = builder().buildMarketOrder(
            UserMarketOrder.builder()
                .tokenID(TOKEN_ID).side(Side.BUY)
                .amount(new BigDecimal("3")).price(new BigDecimal("0.5")).build(),
            opts("0.01", "5"));

        assertTrue(signedShares(signed).compareTo(new BigDecimal("5")) >= 0);
    }

    @Test
    @DisplayName("TC-TMV-016 a taker-style BUY is measured after market-buy precision quantization")
    void takerBuyMeasuredAfterQuantization() {
        // FAK BUYs quantize the taker amount DOWN to 4dp; the minimum must see the quantized figure.
        assertThrows(IllegalArgumentException.class,
            () -> builder().buildOrder(
                UserOrder.builder()
                    .tokenID(TOKEN_ID).side(Side.BUY)
                    .price(new BigDecimal("0.5")).size(new BigDecimal("5")).feeRateBps(0).build(),
                opts("0.01", "5.00005"),
                OrderType.FAK));
    }

    @Test
    @DisplayName("TC-TMV-017 no minimum configured leaves enforcement to the exchange")
    void noMinimumConfiguredPasses() {
        assertDoesNotThrow(() -> limit("0.01", Side.BUY, "0.5", "0.01"));
    }
}
