package com.polymarket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chain enum tests")
class ChainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-CH-001: POLYGON has id 137")
    void polygonId() {
        assertEquals(137, Chain.POLYGON.getId());
    }

    @Test
    @DisplayName("TC-CH-002: AMOY has id 80002")
    void amoyId() {
        assertEquals(80002, Chain.AMOY.getId());
    }

    @Test
    @DisplayName("TC-CH-003: fromId(137) returns POLYGON")
    void fromIdPolygon() {
        assertEquals(Chain.POLYGON, Chain.fromId(137));
    }

    @Test
    @DisplayName("TC-CH-004: fromId(80002) returns AMOY")
    void fromIdAmoy() {
        assertEquals(Chain.AMOY, Chain.fromId(80002));
    }

    @Test
    @DisplayName("TC-CH-005: fromId with unknown id throws IllegalArgumentException")
    void fromIdUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Chain.fromId(1));
    }

    @Test
    @DisplayName("TC-CH-006: JSON serialization produces numeric id")
    void jsonSerialize() throws Exception {
        String json = MAPPER.writeValueAsString(Chain.POLYGON);
        assertEquals("137", json);
    }

    @Test
    @DisplayName("TC-CH-007: JSON deserialization from numeric id")
    void jsonDeserialize() throws Exception {
        Chain chain = MAPPER.readValue("137", Chain.class);
        assertEquals(Chain.POLYGON, chain);
    }

    @Test
    @DisplayName("TC-CH-008: JSON round-trip for AMOY")
    void jsonRoundTripAmoy() throws Exception {
        String json = MAPPER.writeValueAsString(Chain.AMOY);
        Chain roundTripped = MAPPER.readValue(json, Chain.class);
        assertEquals(Chain.AMOY, roundTripped);
    }
}
