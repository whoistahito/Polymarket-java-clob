package com.polymarket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderStatusType enum tests")
class OrderStatusTypeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-OST-001: All known values are present")
    void allKnownValues() {
        assertNotNull(OrderStatusType.LIVE);
        assertNotNull(OrderStatusType.MATCHED);
        assertNotNull(OrderStatusType.CANCELED);
        assertNotNull(OrderStatusType.DELAYED);
        assertNotNull(OrderStatusType.UNMATCHED);
        assertNotNull(OrderStatusType.UNKNOWN);
    }

    @ParameterizedTest(name = "TC-OST-002: fromValue(\"{0}\") returns correct variant")
    @ValueSource(strings = {"LIVE", "MATCHED", "CANCELED", "DELAYED", "UNMATCHED"})
    @DisplayName("TC-OST-002: Known values parse correctly (upper-case)")
    void knownValuesUpperCase(String value) {
        assertNotEquals(OrderStatusType.UNKNOWN, OrderStatusType.fromValue(value));
    }

    @ParameterizedTest(name = "TC-OST-003: fromValue(\"{0}\") lower-case works")
    @ValueSource(strings = {"live", "matched", "canceled", "delayed", "unmatched"})
    @DisplayName("TC-OST-003: Known values parse correctly (lower-case)")
    void knownValuesLowerCase(String value) {
        assertNotEquals(OrderStatusType.UNKNOWN, OrderStatusType.fromValue(value));
    }

    @Test
    @DisplayName("TC-OST-004: Unknown string returns UNKNOWN sentinel")
    void unknownFallback() {
        assertEquals(OrderStatusType.UNKNOWN, OrderStatusType.fromValue("SOME_FUTURE_STATUS"));
    }

    @Test
    @DisplayName("TC-OST-005: null returns UNKNOWN")
    void nullReturnsUnknown() {
        assertEquals(OrderStatusType.UNKNOWN, OrderStatusType.fromValue(null));
    }

    @Test
    @DisplayName("TC-OST-006: JSON deserialization round-trip for LIVE")
    void jsonRoundTrip() throws Exception {
        String json = MAPPER.writeValueAsString(OrderStatusType.LIVE);
        OrderStatusType result = MAPPER.readValue(json, OrderStatusType.class);
        assertEquals(OrderStatusType.LIVE, result);
    }

    @Test
    @DisplayName("TC-OST-007: Unknown JSON value deserializes to UNKNOWN")
    void jsonUnknown() throws Exception {
        OrderStatusType result = MAPPER.readValue("\"FUTURE_STATUS\"", OrderStatusType.class);
        assertEquals(OrderStatusType.UNKNOWN, result);
    }
}
