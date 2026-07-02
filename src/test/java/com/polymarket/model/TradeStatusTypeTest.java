package com.polymarket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeStatusType enum tests")
class TradeStatusTypeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-TST-001: All known values are present")
    void allKnownValues() {
        assertNotNull(TradeStatusType.MATCHED);
        assertNotNull(TradeStatusType.MINED);
        assertNotNull(TradeStatusType.CONFIRMED);
        assertNotNull(TradeStatusType.RETRYING);
        assertNotNull(TradeStatusType.FAILED);
        assertNotNull(TradeStatusType.UNKNOWN);
    }

    @ParameterizedTest(name = "TC-TST-002: fromValue(\"{0}\") upper-case")
    @ValueSource(strings = {"MATCHED", "MINED", "CONFIRMED", "RETRYING", "FAILED"})
    @DisplayName("TC-TST-002: Known values parse correctly (upper-case)")
    void knownValuesUpperCase(String value) {
        assertNotEquals(TradeStatusType.UNKNOWN, TradeStatusType.fromValue(value));
    }

    @ParameterizedTest(name = "TC-TST-003: fromValue(\"{0}\") lower-case")
    @ValueSource(strings = {"matched", "mined", "confirmed", "retrying", "failed"})
    @DisplayName("TC-TST-003: Known values parse correctly (lower-case)")
    void knownValuesLowerCase(String value) {
        assertNotEquals(TradeStatusType.UNKNOWN, TradeStatusType.fromValue(value));
    }

    @Test
    @DisplayName("TC-TST-004: Unknown string returns UNKNOWN")
    void unknownFallback() {
        assertEquals(TradeStatusType.UNKNOWN, TradeStatusType.fromValue("PENDING_CHAIN"));
    }

    @Test
    @DisplayName("TC-TST-005: null returns UNKNOWN")
    void nullReturnsUnknown() {
        assertEquals(TradeStatusType.UNKNOWN, TradeStatusType.fromValue(null));
    }

    @Test
    @DisplayName("TC-TST-006: JSON round-trip for CONFIRMED")
    void jsonRoundTrip() throws Exception {
        String json = MAPPER.writeValueAsString(TradeStatusType.CONFIRMED);
        TradeStatusType result = MAPPER.readValue(json, TradeStatusType.class);
        assertEquals(TradeStatusType.CONFIRMED, result);
    }

    @Test
    @DisplayName("TC-TST-007: Unknown JSON value deserializes to UNKNOWN")
    void jsonUnknown() throws Exception {
        TradeStatusType result = MAPPER.readValue("\"UNKNOWN_STATUS\"", TradeStatusType.class);
        assertEquals(TradeStatusType.UNKNOWN, result);
    }
}
