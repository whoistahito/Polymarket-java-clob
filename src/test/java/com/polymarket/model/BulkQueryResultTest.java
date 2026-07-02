package com.polymarket.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Typed bulk-query result model tests")
class BulkQueryResultTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- SpreadResult (used by single getSpread) ---

    @Test
    @DisplayName("TC-BQ-009: SpreadResult builder with all fields")
    void spreadResultBuilder() {
        SpreadResult r = SpreadResult.builder()
                .tokenId("tok3")
                .spread(new BigDecimal("0.02"))
                .build();
        assertEquals("tok3", r.getTokenId());
        assertEquals(new BigDecimal("0.02"), r.getSpread());
    }

    @Test
    @DisplayName("TC-BQ-010: SpreadResult JSON deserialization")
    void spreadResultJson() throws Exception {
        String json = "{\"token_id\":\"tok3\",\"spread\":\"0.03\"}";
        SpreadResult r = MAPPER.readValue(json, SpreadResult.class);
        assertEquals("tok3", r.getTokenId());
        assertEquals(0, new BigDecimal("0.03").compareTo(r.getSpread()));
    }

    @Test
    @DisplayName("TC-BQ-012: SpreadResult null tokenId allowed")
    void spreadResultNullTokenId() throws Exception {
        // Single-item response may not include token_id (it's known from the request)
        String json = "{\"spread\":\"0.04\"}";
        SpreadResult r = MAPPER.readValue(json, SpreadResult.class);
        assertNull(r.getTokenId());
        assertEquals(0, new BigDecimal("0.04").compareTo(r.getSpread()));
    }

    // --- LastTradePriceResult (its endpoint genuinely returns an array) ---

    @Test
    @DisplayName("TC-BQ-014: LastTradePriceResult JSON deserialization")
    void lastTradePriceResultJson() throws Exception {
        String json = "{\"token_id\":\"tok4\",\"price\":\"0.67\"}";
        LastTradePriceResult r = MAPPER.readValue(json, LastTradePriceResult.class);
        assertEquals("tok4", r.getTokenId());
        assertEquals(0, new BigDecimal("0.67").compareTo(r.getPrice()));
    }

    @Test
    @DisplayName("TC-BQ-015: LastTradePriceResult list deserialization")
    void lastTradePriceResultListJson() throws Exception {
        String json = "[{\"token_id\":\"t1\",\"price\":\"0.70\"},{\"token_id\":\"t2\",\"price\":\"0.25\"}]";
        List<LastTradePriceResult> results = MAPPER.readValue(json, new TypeReference<List<LastTradePriceResult>>() {});
        assertEquals(2, results.size());
        assertEquals("t1", results.get(0).getTokenId());
        assertEquals(0, new BigDecimal("0.70").compareTo(results.get(0).getPrice()));
        assertEquals("t2", results.get(1).getTokenId());
        assertEquals(0, new BigDecimal("0.25").compareTo(results.get(1).getPrice()));
    }

    @Test
    @DisplayName("TC-BQ-016: LastTradePriceResult ignores unknown fields")
    void lastTradePriceResultIgnoresUnknown() throws Exception {
        String json = "{\"token_id\":\"tok4\",\"price\":\"0.60\",\"ts\":1234567890}";
        LastTradePriceResult r = MAPPER.readValue(json, LastTradePriceResult.class);
        assertEquals("tok4", r.getTokenId());
        assertEquals(0, new BigDecimal("0.60").compareTo(r.getPrice()));
    }
}
