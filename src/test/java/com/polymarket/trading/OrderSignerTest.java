package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Offline signing through the public seam: no network dependency, purely a function of its inputs. */
@DisplayName("Public offline order signing (issues #12, #13)")
class OrderSignerTest {

    private static final String KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final PrivateKeySigner LOCAL_SIGNER = PrivateKeySigner.of(KEY);
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("1"), false);

    private final OrderSigner signer = new Eip712OrderSigner();

    @Test
    @DisplayName("TC-OS-001: identical explicit inputs produce identical signed output")
    void deterministicGivenSameInputs() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());
        SigningContext context = SigningContext.of(identity, LOCAL_SIGNER, 42L, Instant.ofEpochSecond(1_800_000_000));

        SignedOrder first = signer.sign(new TokenId("123"), Side.BUY,
                PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);
        SignedOrder second = signer.sign(new TokenId("123"), Side.BUY,
                PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);

        assertEquals(first, second);
    }

    @Test
    @DisplayName("TC-OS-002: builder metadata is preserved when present")
    void builderMetadataPreserved() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());
        String builderCode = "0x" + "1".repeat(64);
        SigningContext context = SigningContext
                .of(identity, LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000))
                .withBuilder(builderCode);

        SignedOrder signed = signer.sign(new TokenId("123"), Side.BUY,
                PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);

        assertEquals(builderCode, signed.builder());
    }

    @Test
    @DisplayName("TC-OS-003: TokenId and PositionId sign against distinct domains for the same order")
    void tokenAndPositionRouteToDistinctExchanges() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());
        Instant now = Instant.ofEpochSecond(1_800_000_000);
        SigningContext context = SigningContext.of(identity, LOCAL_SIGNER, 1L, now);

        SignedOrder tokenOrder = signer.sign(new TokenId("123"), Side.BUY,
                PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);
        SignedOrder positionOrder = signer.sign(new PositionId("123"), Side.BUY,
                PusdAmount.of("5.2"), ShareQuantity.of("10"), RULES, context);

        assertNotEquals(tokenOrder.signature(), positionOrder.signature());
        assertEquals(tokenOrder.timestamp(), positionOrder.timestamp() * 1000,
                "V2 carries milliseconds, V3 carries seconds, for the same instant");
    }

    @Test
    @DisplayName("TC-OS-004: a local signer bound to the wrong identity is rejected before signing")
    void mismatchedSignerIsRejected() {
        SigningIdentity otherIdentity = SigningIdentity.eoa("0x1234567890123456789012345678901234567890");
        assertThrows(IllegalArgumentException.class, () -> SigningContext
                .of(otherIdentity, LOCAL_SIGNER, 1L, Instant.now()));
    }

    @Test
    @DisplayName("TC-OS-005: an off-grid Protected Price never becomes a Signed Order")
    void offGridProtectedPriceIsRejectedBeforeSigning() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> signer.sign(new TokenId("123"), Side.BUY, Price.of("0.525"),
                        ShareQuantity.of("10"), RULES, context));

        assertTrue(e.getMessage().contains("0.525"), e.getMessage());
    }

    @Test
    @DisplayName("TC-OS-006: shares below the live minimum are rejected on both BUY and SELL")
    void belowMinimumSharesAreRejectedOnBothSides() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));
        ShareQuantity tooFew = ShareQuantity.of("0.999999");

        for (Side side : Side.values()) {
            IllegalArgumentException priced = assertThrows(IllegalArgumentException.class,
                    () -> signer.sign(new TokenId("123"), side, Price.of("0.52"), tooFew,
                            RULES, context));
            assertTrue(priced.getMessage().contains("0.999999"), priced.getMessage());

            assertThrows(IllegalArgumentException.class,
                    () -> signer.sign(new TokenId("123"), side, PusdAmount.of("0.52"), tooFew,
                            RULES, context));
        }
    }

    @Test
    @DisplayName("TC-OS-007: an out-of-bounds price is refused before its amount is encoded")
    void outOfBoundsPriceIsRefusedBeforeEncoding() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));

        // Certainty encodes to a perfectly valid 10.0000 pUSD leg, so only the bound can stop it.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> signer.sign(new TokenId("123"), Side.BUY, Price.of("1"),
                        ShareQuantity.of("10"), RULES, context));

        assertTrue(e.getMessage().contains("1"), e.getMessage());
    }

    @Test
    @DisplayName("TC-OS-008: a priced order encodes its legs at the grid's amount precision")
    void pricedOrderEncodesLegsAtTheGridPrecision() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));

        SignedOrder signed = signer.sign(new TokenId("123"), Side.BUY, Price.of("0.52"),
                ShareQuantity.of("10"), RULES, context);

        assertEquals(5_200_000L, signed.makerAmount(), "5.20 pUSD in six-decimal base units");
        assertEquals(10_000_000L, signed.takerAmount(), "10 shares in six-decimal base units");
    }

    @Test
    @DisplayName("TC-OS-009: an order leg worth nothing is refused before signing")
    void aZeroLegIsRefused() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));
        MarketRules noMinimum = new MarketRules(TickSize.of("0.01"), ShareQuantity.of("0"), false);

        assertThrows(IllegalArgumentException.class, () -> signer.sign(new TokenId("123"),
                Side.BUY, PusdAmount.of("0"), ShareQuantity.of("10"), noMinimum, context));
        assertThrows(IllegalArgumentException.class, () -> signer.sign(new TokenId("123"),
                Side.BUY, PusdAmount.of("5.2"), ShareQuantity.of("0"), noMinimum, context));
    }

    @Test
    @DisplayName("TC-OS-010: an asset identifier that is not a uint256 cannot reach the signer")
    void anInvalidIdentifierFailsAtItsOwnBoundary() {
        // The signer takes a sealed AssetId, so a malformed identifier fails before it exists.
        assertThrows(IllegalArgumentException.class, () -> new TokenId("0xabc"));
        assertThrows(IllegalArgumentException.class, () -> new TokenId("-1"));
        assertThrows(IllegalArgumentException.class, () -> new TokenId("12.5"));
    }

    @Test
    @DisplayName("TC-OS-011: a V3 Combo order names the Trading Wallet as maker and the Account Signer as signer")
    void v3KeepsTheAccountSignerDistinctFromTheTradingWallet() {
        String tradingWallet = "0x" + "b".repeat(40);
        PositionId combo = new PositionId("77");

        for (SigningIdentity identity : List.of(
                SigningIdentity.proxyWallet(tradingWallet, LOCAL_SIGNER.address()),
                SigningIdentity.safeWallet(tradingWallet, LOCAL_SIGNER.address()))) {
            SignedOrder order = signer.sign(combo, Side.BUY, PusdAmount.of("5"), ShareQuantity.of("10"),
                    RULES, SigningContext.of(identity, LOCAL_SIGNER, 7L, Instant.ofEpochSecond(1_800_000_000)));

            assertEquals(tradingWallet, order.maker(), "the Trading Wallet holds the position");
            assertEquals(LOCAL_SIGNER.address(), order.signer(), "the Account Signer authorizes it");
            assertNotEquals(order.maker(), order.signer());
        }

        SignedOrder deposit = signer.sign(combo, Side.BUY, PusdAmount.of("5"), ShareQuantity.of("10"), RULES,
                SigningContext.of(SigningIdentity.depositWallet(tradingWallet, LOCAL_SIGNER.address()),
                        LOCAL_SIGNER, 7L, Instant.ofEpochSecond(1_800_000_000)));
        assertEquals(tradingWallet, deposit.maker());
        assertEquals(LOCAL_SIGNER.address(), deposit.signer());
    }

    @Test
    @DisplayName("TC-OS-012: a negative salt or timestamp cannot be signed as an unsigned field")
    void unsignedSigningValuesFailAtTheirDomainBoundary() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());

        assertThrows(IllegalArgumentException.class, () -> SigningContext.of(
                identity, LOCAL_SIGNER, -1L, Instant.ofEpochSecond(1_800_000_000)),
                "salt is a uint256 on the wire");
        assertThrows(IllegalArgumentException.class, () -> SigningContext.of(
                identity, LOCAL_SIGNER, 7L, Instant.ofEpochSecond(-1)),
                "timestamp is a uint256 on the wire");
    }
}
