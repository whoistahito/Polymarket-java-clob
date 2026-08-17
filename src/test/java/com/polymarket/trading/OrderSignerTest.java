package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
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
}
