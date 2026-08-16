package com.polymarket.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Pins the official 2026 protocol contracts. Expected values come from Polymarket's published
 * documentation and an independent signer, never from this SDK's production code.
 */
@DisplayName("Official 2026 protocol contracts and signing vectors")
class ProtocolContractsTest {

    // Published verbatim by Polymarket in the "Sign the Order" examples for both exchanges:
    // docs.polymarket.com/trading/place-orders and /trading/combos/market-makers.
    private static final String OFFICIAL_ORDER_ENCODE_TYPE =
            "Order(uint256 salt,address maker,address signer,uint256 tokenId,uint256 makerAmount,"
                    + "uint256 takerAmount,uint8 side,uint8 signatureType,uint256 timestamp,"
                    + "bytes32 metadata,bytes32 builder)";

    private static final String OFFICIAL_TYPED_DATA_SIGN_ENCODE_TYPE =
            "TypedDataSign(Order contents,string name,string version,uint256 chainId,"
                    + "address verifyingContract,bytes32 salt)"
                    + OFFICIAL_ORDER_ENCODE_TYPE;

    private static final JsonNode VECTORS = load("/protocol/signing-vectors.json");
    private static final JsonNode CONSTRAINTS = load("/protocol/constraints.json");
    private static final JsonNode GATEWAY = load("/protocol/builder-gateway.json");

    private static JsonNode load(String resource) {
        try (InputStream in = ProtocolContractsTest.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("missing protocol fixture " + resource, e);
        }
    }

    static List<JsonNode> vectors() {
        List<JsonNode> all = new ArrayList<>();
        VECTORS.get("vectors").forEach(all::add);
        return all;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorIds")
    @DisplayName("TC-PC-001: each vector encodes the official Order type verbatim")
    void vectorsEncodeTheOfficialOrderType(String id) {
        JsonNode v = vector(id);
        String expected = "TypedDataSign".equals(v.get("primaryType").asText())
                ? OFFICIAL_TYPED_DATA_SIGN_ENCODE_TYPE
                : OFFICIAL_ORDER_ENCODE_TYPE;
        assertEquals(expected, v.get("encodeType").asText());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorIds")
    @DisplayName("TC-PC-002: each vector's signature recovers to its stated signer over its digest")
    void vectorSignaturesRecoverToTheStatedSigner(String id) {
        JsonNode v = vector(id);
        byte[] digest = Numeric.hexStringToByteArray(v.get("digest").asText());
        byte[] sig = Numeric.hexStringToByteArray(v.get("signature").asText());
        assertEquals(65, sig.length, "EIP-712 signatures are 65 bytes");

        byte header = sig[64];
        Sign.SignatureData data = new Sign.SignatureData(
                (byte) (header < 27 ? header + 27 : header),
                java.util.Arrays.copyOfRange(sig, 0, 32),
                java.util.Arrays.copyOfRange(sig, 32, 64));

        BigInteger publicKey;
        try {
            publicKey = Sign.signedMessageHashToKey(digest, data);
        } catch (Exception e) {
            throw new AssertionError("could not recover signer for vector " + id, e);
        }
        assertEquals(v.get("signerAddress").asText().toLowerCase(Locale.ROOT),
                Numeric.prependHexPrefix(Keys.getAddress(publicKey)));
    }

    @Test
    @DisplayName("TC-PC-003: V2 and V3 sign under different domains and exchange contracts")
    void v2AndV3AreDistinctDomains() {
        JsonNode contracts = CONSTRAINTS.get("contracts");
        JsonNode v2 = vector("v2-eoa").get("domain");
        JsonNode v3 = vector("v3-eoa").get("domain");

        assertEquals("2", v2.get("version").asText());
        assertEquals("3", v3.get("version").asText());
        assertEquals(contracts.get("ctfExchangeV2").asText(), v2.get("verifyingContract").asText());
        assertEquals(contracts.get("combosExchangeV3").asText(), v3.get("verifyingContract").asText());
        assertEquals(137, v2.get("chainId").asInt());
        assertEquals(137, v3.get("chainId").asInt());
        assertNotEquals(vector("v2-eoa").get("domainSeparator").asText(),
                vector("v3-eoa").get("domainSeparator").asText());
    }

    @Test
    @DisplayName("TC-PC-004: Deposit Wallet vectors wrap the order for ERC-7739 validation")
    void depositWalletVectorsAreWrapped() {
        for (String id : List.of("v2-deposit-wallet", "v3-deposit-wallet")) {
            JsonNode message = vector(id).get("message");
            assertEquals("DepositWallet", message.get("name").asText(), id);
            assertEquals("1", message.get("version").asText(), id);
            assertEquals(3, message.get("contents").get("signatureType").asInt(), id);
            assertNotEquals(vector(id).get("domain").get("verifyingContract").asText(),
                    message.get("verifyingContract").asText(),
                    id + ": the wrapper verifies against the wallet, not the exchange");
        }
        assertTrue(CONSTRAINTS.get("signatureTypes").get("depositWalletSignatureIsErc7739Wrapped").asBoolean());
    }

    @Test
    @DisplayName("TC-PC-005: V2 and V3 order timestamps keep their conflicting official units")
    void timestampUnitsStaySeparate() {
        JsonNode struct = CONSTRAINTS.get("orderStruct");
        assertEquals("unix-milliseconds", struct.get("v2TimestampUnit").asText());
        assertEquals("unix-seconds", struct.get("v3TimestampUnit").asText());
        assertFalse(struct.get("expirationIsSignedField").asBoolean(),
                "expiration rides the wire body but is not signed");
        List<String> fields = new ArrayList<>();
        vector("v2-eoa").get("types").get("Order")
                .forEach(f -> fields.add(f.get("name").asText()));
        for (String removed : List.of("nonce", "feeRateBps", "taker")) {
            assertFalse(fields.contains(removed),
                    removed + " was removed in V2 and must not appear in the signed struct");
        }
        assertTrue(fields.containsAll(List.of("timestamp", "metadata", "builder")));
    }

    @Test
    @DisplayName("TC-PC-006: Gamma notional never substitutes for the CLOB minimum-share rule")
    void minimumSizeSourcesStaySeparate() {
        JsonNode clob = CONSTRAINTS.get("minimumSize").get("clobSigningRule");
        JsonNode gamma = CONSTRAINTS.get("minimumSize").get("gammaDiscoveryMetadata");

        assertTrue(clob.get("authoritativeForSigning").asBoolean());
        assertFalse(gamma.get("authoritativeForSigning").asBoolean());
        assertNotEquals(clob.get("unit").asText(), gamma.get("unit").asText());
        assertNotEquals(clob.get("field").asText(), gamma.get("field").asText());
    }

    @Test
    @DisplayName("TC-PC-007: the Builder Gateway requester flow is pinned to three endpoints")
    void builderGatewayRequesterFlowIsPinned() {
        List<String> routes = new ArrayList<>();
        GATEWAY.get("endpoints").forEach(e -> routes.add(
                e.get("method").asText() + " " + e.get("path").asText()
                        + (e.get("builderAuth").asBoolean() ? " +builder" : "")));

        assertEquals(List.of(
                "POST /v1/builder/rfq/requests +builder",
                "POST /v1/builder/rfq/requests/{rfq_id}/accept +builder",
                "GET /v1/builder/rfq/requests/{rfq_id}"), routes);

        JsonNode statuses = GATEWAY.get("statuses");
        assertEquals(List.of("FAILED", "EXPIRED", "CANCELED"),
                textList(statuses.get("terminalWithoutFill")));
        assertEquals(List.of("CONFIRMED", "FILLED"), textList(statuses.get("success")));
    }

    @Test
    @DisplayName("TC-PC-008: settlement resolves through trade IDs, not inline transaction hashes")
    void settlementIsReconciledThroughTradeIds() {
        JsonNode settlement = CONSTRAINTS.get("settlement");
        assertFalse(settlement.get("postOrderReturnsTransactionHashes").asBoolean());
        assertTrue(settlement.get("postOrderReturnsTradeIds").asBoolean());
    }

    @Test
    @DisplayName("TC-PC-009: official batch and GTD limits are recorded as testable numbers")
    void officialLimitsArePinned() {
        assertEquals(15, CONSTRAINTS.get("batchLimits").get("postOrdersMax").asInt());
        assertEquals(1000, CONSTRAINTS.get("batchLimits").get("cancelOrdersMax").asInt());

        JsonNode gtd = CONSTRAINTS.get("gtd");
        assertEquals(60, gtd.get("securityThresholdSeconds").asInt());
        assertEquals(180, gtd.get("minimumFutureSeconds").asInt());
        assertEquals("unix-seconds", gtd.get("expirationUnit").asText());
    }

    static List<String> vectorIds() {
        return vectors().stream().map(v -> v.get("id").asText()).toList();
    }

    private static JsonNode vector(String id) {
        return vectors().stream()
                .filter(v -> id.equals(v.get("id").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no vector " + id));
    }

    private static List<String> textList(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
