package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PMK-014: side omitted when null (Rust skip_serializing_none). */
@DisplayName("TC-BP — BookParams serialization")
class BookParamsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("TC-BP-001 side unset is omitted from JSON")
    void sideUnsetOmitted() throws Exception {
        BookParams params = BookParams.builder().tokenId("123").build();

        String json = mapper.writeValueAsString(params);

        assertFalse(json.contains("\"side\""), "JSON should have no \"side\" key: " + json);
    }

    @Test
    @DisplayName("TC-BP-002 side BUY is serialized")
    void sideBuySerialized() throws Exception {
        BookParams params = BookParams.builder().tokenId("123").side(Side.BUY).build();

        String json = mapper.writeValueAsString(params);

        assertTrue(json.contains("\"side\":\"BUY\""), "JSON should contain \"side\":\"BUY\": " + json);
    }
}
