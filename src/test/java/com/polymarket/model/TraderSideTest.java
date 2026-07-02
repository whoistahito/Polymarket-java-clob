package com.polymarket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TraderSide enum tests")
class TraderSideTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-TRS-001: TAKER fromValue")
    void takerFromValue() {
        assertEquals(TraderSide.TAKER, TraderSide.fromValue("TAKER"));
    }

    @Test
    @DisplayName("TC-TRS-002: MAKER fromValue")
    void makerFromValue() {
        assertEquals(TraderSide.MAKER, TraderSide.fromValue("MAKER"));
    }

    @Test
    @DisplayName("TC-TRS-003: case-insensitive parsing")
    void caseInsensitive() {
        assertEquals(TraderSide.TAKER, TraderSide.fromValue("taker"));
        assertEquals(TraderSide.MAKER, TraderSide.fromValue("maker"));
    }

    @Test
    @DisplayName("TC-TRS-004: Unknown string returns UNKNOWN")
    void unknownFallback() {
        assertEquals(TraderSide.UNKNOWN, TraderSide.fromValue("BOTH"));
    }

    @Test
    @DisplayName("TC-TRS-005: null returns UNKNOWN")
    void nullReturnsUnknown() {
        assertEquals(TraderSide.UNKNOWN, TraderSide.fromValue(null));
    }

    @Test
    @DisplayName("TC-TRS-006: JSON round-trip for MAKER")
    void jsonRoundTrip() throws Exception {
        String json = MAPPER.writeValueAsString(TraderSide.MAKER);
        TraderSide result = MAPPER.readValue(json, TraderSide.class);
        assertEquals(TraderSide.MAKER, result);
    }
}
