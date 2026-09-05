package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.rfq.ComboQuoteSigner;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Offline signing through the public seam: no network dependency, purely a function of its inputs. */
class OrderSignerTest {

    private static final String KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final PrivateKeySigner LOCAL_SIGNER = PrivateKeySigner.of(KEY);
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("1"), false);

    private final Eip712OrderSigner eip712 = new Eip712OrderSigner();
    private final OrderSigner signer = eip712;
    private final ComboQuoteSigner comboSigner = eip712;

    @Test
    void shouldBeDeterministicWhenInputsAreIdentical() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());
        SigningContext context = SigningContext.of(identity, LOCAL_SIGNER, 42L, Instant.ofEpochSecond(1_800_000_000));

        SignedOrder first = signer.sign(new TokenId("123"), Side.BUY,
                Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
        SignedOrder second = signer.sign(new TokenId("123"), Side.BUY,
                Price.of("0.52"), ShareQuantity.of("10"), RULES, context);

        assertEquals(first, second);
    }

    @Test
    void shouldPreserveBuilderMetadataWhenBuilderIsPresent() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());
        String builderCode = "0x" + "1".repeat(64);
        SigningContext context = SigningContext
                .of(identity, LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000))
                .withBuilder(builderCode);

        SignedOrder signed = signer.sign(new TokenId("123"), Side.BUY,
                Price.of("0.52"), ShareQuantity.of("10"), RULES, context);

        assertEquals(builderCode, signed.builder());
    }

    @Test
    void shouldUseDistinctExchangeDomainsWhenAssetTypesDiffer() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());
        Instant now = Instant.ofEpochSecond(1_800_000_000);
        SigningContext context = SigningContext.of(identity, LOCAL_SIGNER, 1L, now);

        SignedOrder tokenOrder = signer.sign(new TokenId("123"), Side.BUY,
                Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
        SignedOrder positionOrder = signer.sign(new PositionId("123"), Side.BUY,
                Price.of("0.52"), ShareQuantity.of("10"), RULES, context);

        assertNotEquals(tokenOrder.signature(), positionOrder.signature());
        assertEquals(tokenOrder.timestamp(), positionOrder.timestamp() * 1000,
                "V2 carries milliseconds, V3 carries seconds, for the same instant");
    }

    @Test
    void shouldThrowWhenSignerIdentityDoesNotMatch() {
        SigningIdentity otherIdentity = SigningIdentity.eoa("0x1234567890123456789012345678901234567890");
        assertThrows(IllegalArgumentException.class, () -> SigningContext
                .of(otherIdentity, LOCAL_SIGNER, 1L, Instant.now()));
    }

    @Test
    void shouldThrowForOffGridPriceWhenSigningOrder() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> signer.sign(new TokenId("123"), Side.BUY, Price.of("0.525"),
                        ShareQuantity.of("10"), RULES, context));

        assertTrue(e.getMessage().contains("0.525"), e.getMessage());
    }

    @Test
    void shouldThrowForBelowMinimumSharesWhenSigningEitherSide() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));
        ShareQuantity tooFew = ShareQuantity.of("0.999999");

        for (Side side : Side.values()) {
            IllegalArgumentException priced = assertThrows(IllegalArgumentException.class,
                    () -> signer.sign(new TokenId("123"), side, Price.of("0.52"), tooFew,
                            RULES, context));
            assertTrue(priced.getMessage().contains("0.999999"), priced.getMessage());

        }
    }

    @Test
    void shouldThrowForOutOfBoundsPriceWhenEncodingOrder() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));

        // Certainty encodes to a perfectly valid 10.0000 pUSD leg, so only the bound can stop it.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> signer.sign(new TokenId("123"), Side.BUY, Price.of("1"),
                        ShareQuantity.of("10"), RULES, context));

        assertTrue(e.getMessage().contains("1"), e.getMessage());
    }

    @Test
    void shouldEncodeOrderLegsAtGridPrecisionWhenPriceAndSizeAreValid() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));

        SignedOrder signed = signer.sign(new TokenId("123"), Side.BUY, Price.of("0.52"),
                ShareQuantity.of("10"), RULES, context);

        assertEquals(5_200_000L, signed.makerAmount(), "5.20 pUSD in six-decimal base units");
        assertEquals(10_000_000L, signed.takerAmount(), "10 shares in six-decimal base units");
    }

    @Test
    void shouldThrowForZeroLegWhenSigningComboOrder() {
        SigningContext context = SigningContext.of(SigningIdentity.eoa(LOCAL_SIGNER.address()),
                LOCAL_SIGNER, 1L, Instant.ofEpochSecond(1_800_000_000));
        assertThrows(IllegalArgumentException.class, () -> comboSigner.sign(new PositionId("77"),
                Side.BUY, PusdAmount.of("0"), ShareQuantity.of("10"), context));
        assertThrows(IllegalArgumentException.class, () -> comboSigner.sign(new PositionId("77"),
                Side.BUY, PusdAmount.of("5.2"), ShareQuantity.of("0"), context));
    }

    @Test
    void shouldThrowForInvalidAssetIdentifierWhenConstructingTokenId() {
        // The signer takes a sealed AssetId, so a malformed identifier fails before it exists.
        assertThrows(IllegalArgumentException.class, () -> new TokenId("0xabc"));
        assertThrows(IllegalArgumentException.class, () -> new TokenId("-1"));
        assertThrows(IllegalArgumentException.class, () -> new TokenId("12.5"));
    }

    @Test
    void shouldKeepAccountSignerDistinctWhenSigningV3WalletOrder() {
        String tradingWallet = "0x" + "b".repeat(40);
        PositionId combo = new PositionId("77");

        for (SigningIdentity identity : List.of(
                SigningIdentity.proxyWallet(tradingWallet, LOCAL_SIGNER.address()),
                SigningIdentity.safeWallet(tradingWallet, LOCAL_SIGNER.address()))) {
            SignedOrder order = comboSigner.sign(combo, Side.BUY, PusdAmount.of("5"), ShareQuantity.of("10"),
                    SigningContext.of(identity, LOCAL_SIGNER, 7L, Instant.ofEpochSecond(1_800_000_000)));

            assertEquals(tradingWallet, order.maker(), "the Trading Wallet holds the position");
            assertEquals(LOCAL_SIGNER.address(), order.signer(), "the Account Signer authorizes it");
            assertNotEquals(order.maker(), order.signer());
        }

        SignedOrder deposit = comboSigner.sign(combo, Side.BUY, PusdAmount.of("5"), ShareQuantity.of("10"),
                SigningContext.of(SigningIdentity.depositWallet(tradingWallet, LOCAL_SIGNER.address()),
                        LOCAL_SIGNER, 7L, Instant.ofEpochSecond(1_800_000_000)));
        assertEquals(tradingWallet, deposit.maker());
        assertEquals(tradingWallet, deposit.signer(),
                "type 3 is ERC-1271-verified by the wallet, so the wallet is the resolved signer");
        assertEquals(LOCAL_SIGNER.address(), deposit.accountSigner(),
                "the Account Signer still produced the signature and still authenticates L2");
    }

    @Test
    void shouldExposeOnlyPricedSignSeamWhenInspectingOrderSigner() {
        List<Method> seams = Stream.of(OrderSigner.class.getMethods())
                .filter(m -> m.getName().equals("sign")).toList();

        assertEquals(1, seams.size(),
                "an unpriced overload lets a caller imply a price the snapshot never saw");
        assertTrue(List.of(seams.get(0).getParameterTypes()).contains(Price.class),
                "the signing seam must take the Protected Price it is meant to enforce");
    }

    @Test
    void shouldRestrictComboSignSeamToPositionsWhenInspectingQuoteSigner() {
        for (Method seam : ComboQuoteSigner.class.getMethods()) {
            if (!seam.getName().equals("sign")) continue;
            assertEquals(PositionId.class, seam.getParameterTypes()[0],
                    "a token order has a tick grid, so it must not reach the unpriced Combo seam");
        }
    }

    @Test
    void shouldThrowForNegativeSigningValueWhenConstructingSigningContext() {
        SigningIdentity identity = SigningIdentity.eoa(LOCAL_SIGNER.address());

        assertThrows(IllegalArgumentException.class, () -> SigningContext.of(
                identity, LOCAL_SIGNER, -1L, Instant.ofEpochSecond(1_800_000_000)),
                "salt is a uint256 on the wire");
        assertThrows(IllegalArgumentException.class, () -> SigningContext.of(
                identity, LOCAL_SIGNER, 7L, Instant.ofEpochSecond(-1)),
                "timestamp is a uint256 on the wire");
    }
}
