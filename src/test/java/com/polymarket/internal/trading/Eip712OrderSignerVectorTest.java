package com.polymarket.internal.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves production V2/V3 signing reproduces the official vectors byte-for-byte. Every expected
 * value is read straight from the fixture, never retyped, so there is no independent transcription
 * to drift from {@code ProtocolContractsTest}'s own reading of the same file.
 */
@DisplayName("Production V2/V3 signing matches the official vectors (issues #12, #13)")
class Eip712OrderSignerVectorTest {

    private static final JsonNode VECTORS = load();
    private static final Eip712OrderSigner SIGNER = new Eip712OrderSigner();

    private static JsonNode load() {
        try (InputStream in = Eip712OrderSignerVectorTest.class
                .getResourceAsStream("/protocol/signing-vectors.json")) {
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("missing protocol fixture", e);
        }
    }

    static List<String> vectorIds() {
        List<String> ids = new ArrayList<>();
        VECTORS.get("vectors").forEach(v -> ids.add(v.get("id").asText()));
        return ids;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorIds")
    @DisplayName("TC-ES-001: signature matches the vector's exact bytes")
    void signatureMatchesVector(String id) {
        JsonNode vector = vector(id);
        PrivateKeySigner localSigner = PrivateKeySigner.of(VECTORS.get("privateKey").asText());
        JsonNode domain = vector.get("domain");
        JsonNode order = "TypedDataSign".equals(vector.get("primaryType").asText())
                ? vector.get("message").get("contents")
                : vector.get("message");

        String maker = order.get("maker").asText();
        String signer = order.get("signer").asText();
        int signatureType = order.get("signatureType").asInt();
        SigningIdentity identity = switch (signatureType) {
            case 0 -> SigningIdentity.eoa(signer);
            case 1 -> SigningIdentity.proxyWallet(maker, signer);
            case 2 -> SigningIdentity.safeWallet(maker, signer);
            case 3 -> SigningIdentity.depositWallet(maker, signer);
            default -> throw new IllegalArgumentException("unknown signatureType " + signatureType);
        };

        String version = domain.get("version").asText();
        AssetId asset = "2".equals(version)
                ? new TokenId(order.get("tokenId").asText())
                : new PositionId(order.get("tokenId").asText());
        boolean negRisk = id.contains("neg-risk");
        MarketRules rules = new MarketRules(TickSize.of("0.01"), ShareQuantity.of("0.01"), negRisk);

        long salt = order.get("salt").asLong();
        long rawTimestamp = order.get("timestamp").asLong();
        Instant timestamp = "2".equals(version)
                ? Instant.ofEpochMilli(rawTimestamp)
                : Instant.ofEpochSecond(rawTimestamp);

        int side = order.get("side").asInt();
        BigDecimal makerUnits = new BigDecimal(order.get("makerAmount").asText())
                .movePointLeft(6);
        BigDecimal takerUnits = new BigDecimal(order.get("takerAmount").asText())
                .movePointLeft(6);
        PusdAmount pusdLeg = PusdAmount.of(side == 0 ? makerUnits : takerUnits);
        ShareQuantity shareLeg = ShareQuantity.of(side == 0 ? takerUnits : makerUnits);

        SigningContext context = SigningContext.of(identity, localSigner, salt, timestamp)
                .withMetadata(order.get("metadata").asText())
                .withBuilder(order.get("builder").asText());

        // A V2 token order carries a tick grid, so it is signed through the priced CLOB seam; a V3
        // Combo quote is priced by its maker, so its exact base-unit legs are signed verbatim.
        SignedOrder signed = asset instanceof PositionId position
                ? SIGNER.sign(position, side == 0 ? Side.BUY : Side.SELL, pusdLeg, shareLeg, context)
                : SIGNER.sign(asset, side == 0 ? Side.BUY : Side.SELL,
                        Price.of(pusdLeg.value().divide(shareLeg.value(), MathContext.DECIMAL64)),
                        shareLeg, rules, context);

        // A Deposit Wallet is a contract: the exchange's ERC-1271 check verifies the whole
        // ERC-7739 envelope, of which the inner ECDSA signature is only the first 65 bytes.
        JsonNode expected = vector.has("wrappedSignature")
                ? vector.get("wrappedSignature") : vector.get("signature");
        assertEquals(expected.asText(), signed.signature(), id);
        assertEquals(order.get("makerAmount").asText(), String.valueOf(signed.makerAmount()), id);
        assertEquals(order.get("takerAmount").asText(), String.valueOf(signed.takerAmount()), id);
        assertEquals(rawTimestamp, signed.timestamp(), id);
    }

    private static JsonNode vector(String id) {
        for (JsonNode v : VECTORS.get("vectors")) {
            if (id.equals(v.get("id").asText())) {
                return v;
            }
        }
        throw new IllegalStateException("no vector " + id);
    }
}
