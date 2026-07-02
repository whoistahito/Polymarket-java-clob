package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the PMK-002 Gherkin scenarios: V1/V2 order model shapes and the {@code POLY_1271}
 * signature type. Ground truth: {@code rs-clob-client/src/clob/types/mod.rs}.
 */
@DisplayName("PMK-002 V2 order models")
class V2OrderModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BYTES32_ZERO =
        "0x0000000000000000000000000000000000000000000000000000000000000000";

    // Scenario: Signature type enum is complete
    @Test
    @DisplayName("TC-V2M-001: SignatureType exposes EOA(0), POLY_PROXY(1), POLY_GNOSIS_SAFE(2), POLY_1271(3)")
    void signatureTypeIsComplete() {
        assertEquals(0, SignatureType.EOA.getValue());
        assertEquals(1, SignatureType.POLY_PROXY.getValue());
        assertEquals(2, SignatureType.POLY_GNOSIS_SAFE.getValue());
        assertEquals(3, SignatureType.POLY_1271.getValue());
    }

    @Test
    @DisplayName("TC-V2M-002: POLY_1271 round-trips through JSON as on-chain value 3")
    void poly1271JsonRoundTrip() throws Exception {
        String json = MAPPER.writeValueAsString(SignatureType.POLY_1271);
        assertEquals("3", json);
        assertEquals(SignatureType.POLY_1271, MAPPER.readValue("3", SignatureType.class));
    }

    // Scenario: V2 struct carries the V2 fields
    @Test
    @DisplayName("TC-V2M-003: V2 payload contains timestamp/metadata/builder and not taker/nonce/feeRateBps")
    void v2PayloadShape() throws Exception {
        OrderDataV2 order = OrderDataV2.builder()
            .salt(BigInteger.valueOf(42))
            .maker("0xMaker")
            .signer("0xSigner")
            .tokenId("123")
            .makerAmount(BigInteger.valueOf(1_000_000))
            .takerAmount(BigInteger.valueOf(2_000_000))
            .side(Side.BUY)
            .signatureType(SignatureType.POLY_1271)
            .timestamp(BigInteger.valueOf(1_700_000_000L))
            .build();

        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(order));
        Set<String> fields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);

        assertTrue(fields.contains("timestamp"), "V2 must contain timestamp");
        assertTrue(fields.contains("metadata"), "V2 must contain metadata");
        assertTrue(fields.contains("builder"), "V2 must contain builder");

        assertFalse(fields.contains("taker"), "V2 must NOT contain taker");
        assertFalse(fields.contains("nonce"), "V2 must NOT contain nonce");
        assertFalse(fields.contains("feeRateBps"), "V2 must NOT contain feeRateBps");
        assertFalse(fields.contains("expiration"), "V2 signed struct must NOT contain expiration");
    }

    @Test
    @DisplayName("TC-V2M-004: unset metadata/builder default to bytes32 zero (field + JSON)")
    void bytes32ZeroDefault() throws Exception {
        OrderDataV2 order = OrderDataV2.builder()
            .salt(BigInteger.ONE)
            .maker("0xMaker")
            .tokenId("1")
            .makerAmount(BigInteger.TEN)
            .takerAmount(BigInteger.TEN)
            .side(Side.SELL)
            .timestamp(BigInteger.ZERO)
            .build();

        assertEquals(BYTES32_ZERO, order.getMetadata());
        assertEquals(BYTES32_ZERO, order.getBuilder());

        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(order));
        assertEquals(BYTES32_ZERO, node.get("metadata").asText());
        assertEquals(BYTES32_ZERO, node.get("builder").asText());
    }

    // Scenario: V1 struct is unchanged
    @Test
    @DisplayName("TC-V2M-005: V1 SignedOrder still carries taker/expiration/nonce/feeRateBps")
    void v1Unchanged() throws Exception {
        SignedOrder order = SignedOrder.builder()
            .salt(1L)
            .maker("0xMaker")
            .signer("0xSigner")
            .taker("0xTaker")
            .tokenId("123")
            .makerAmount("1000000")
            .takerAmount("2000000")
            .expiration("0")
            .nonce("0")
            .feeRateBps("0")
            .version(1)
            .side(Side.BUY)
            .signatureType(SignatureType.EOA)
            .signature("0xsig")
            .build();

        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(order));
        assertNotNull(node.get("taker"));
        assertNotNull(node.get("expiration"));
        assertNotNull(node.get("nonce"));
        assertNotNull(node.get("feeRateBps"));
    }
}
