package com.polymarket.util;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TC-PU-DJ — PriceUtils decimal-places and order-JSON serialisation tests")
class PriceUtilsDecimalAndOrderJsonTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // decimalPlaces
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PU4-001: decimalPlaces(\"0.1\") == 1")
    void decimalPlaces01() {
        assertEquals(1, PriceUtils.decimalPlaces("0.1"));
    }

    @Test
    @DisplayName("TC-PU4-002: decimalPlaces(\"0.01\") == 2")
    void decimalPlaces001() {
        assertEquals(2, PriceUtils.decimalPlaces("0.01"));
    }

    @Test
    @DisplayName("TC-PU4-003: decimalPlaces(\"0.001\") == 3")
    void decimalPlaces0001() {
        assertEquals(3, PriceUtils.decimalPlaces("0.001"));
    }

    @Test
    @DisplayName("TC-PU4-004: decimalPlaces(\"0.0001\") == 4")
    void decimalPlaces00001() {
        assertEquals(4, PriceUtils.decimalPlaces("0.0001"));
    }

    @Test
    @DisplayName("TC-PU4-005: decimalPlaces with no dot returns 0")
    void decimalPlacesNoDot() {
        assertEquals(0, PriceUtils.decimalPlaces("1"));
    }

    @Test
    @DisplayName("TC-PU4-006: decimalPlaces(null) returns 0")
    void decimalPlacesNull() {
        assertEquals(0, PriceUtils.decimalPlaces(null));
    }

    // -------------------------------------------------------------------------
    // orderToJson
    // -------------------------------------------------------------------------

    private static SignedOrder dummyOrder() {
        return SignedOrder.builder()
                .salt(1L)
                .maker("0xmaker")
                .signer("0xsigner")
                .taker("0xtaker")
                .tokenId("tok-123")
                .makerAmount("1000000")
                .takerAmount("650000")
                .expiration("0")
                .nonce("0")
                .feeRateBps("0")
                .side(Side.BUY)
                .signatureType(SignatureType.EOA)
                .signature("0xsig")
                .build();
    }

    @Test
    @DisplayName("TC-PU4-007: orderToJson happy path (GTC)")
    void orderToJsonGtcHappyPath() {
        PostOrderPayload payload = PriceUtils.orderToJson(
                dummyOrder(), "owner-key", OrderType.GTC, false, false);
        assertNotNull(payload);
        assertEquals(OrderType.GTC, payload.orderType());
        assertEquals("owner-key", payload.owner());
        assertFalse(payload.deferExec());
    }

    @Test
    @DisplayName("TC-PU4-008: postOnly=true + FOK throws IllegalArgumentException")
    void postOnlyFokThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PriceUtils.orderToJson(dummyOrder(), "owner", OrderType.FOK, false, true));
    }

    @Test
    @DisplayName("TC-PU4-009: postOnly=true + FAK throws IllegalArgumentException")
    void postOnlyFakThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PriceUtils.orderToJson(dummyOrder(), "owner", OrderType.FAK, false, true));
    }

    @Test
    @DisplayName("TC-PU4-010: postOnly=false + FOK does not throw")
    void postOnlyFalseFokOk() {
        assertDoesNotThrow(() ->
                PriceUtils.orderToJson(dummyOrder(), "owner", OrderType.FOK, false, false));
    }

    @Test
    @DisplayName("TC-PU4-011: postOnly=true + GTC does not throw")
    void postOnlyTrueGtcOk() {
        assertDoesNotThrow(() ->
                PriceUtils.orderToJson(dummyOrder(), "owner", OrderType.GTC, false, true));
    }

  @Test
  @DisplayName("TC-PU4-012: order payload serializes signatureType as numeric")
  void signatureTypeSerializesAsNumeric() throws Exception {
    PostOrderPayload payload =
        PostOrderPayload.builder()
            .order(dummyOrder())
            .owner("owner")
            .orderType(OrderType.GTC)
            .deferExec(false)
            .postOnly(false)
            .build();

    String json = MAPPER.writeValueAsString(payload);
    assertTrue(json.contains("\"signatureType\":0"));
    assertFalse(json.contains("\"signatureType\":\"EOA\""));
  }

  @Test
  @DisplayName("TC-PU4-013: order payload omits postOnly when null")
  void postOnlyNullIsOmitted() throws Exception {
    PostOrderPayload payload =
        PostOrderPayload.builder()
            .order(dummyOrder())
            .owner("owner")
            .orderType(OrderType.FOK)
            .deferExec(false)
            .postOnly(null)
            .build();

    String json = MAPPER.writeValueAsString(payload);
    assertFalse(json.contains("\"postOnly\""));
  }
}
