package com.polymarket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Token model tests")
class TokenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-TK-002: winner defaults to false")
    void winnerDefaultsFalse() {
        Token t = Token.builder()
                .tokenId("456")
                .outcome("NO")
                .build();
        assertFalse(t.isWinner());
    }

    @Test
    @DisplayName("TC-TK-003: JSON deserialization")
    void jsonDeserialize() throws Exception {
        String json = "{\"token_id\":\"789\",\"outcome\":\"YES\",\"price\":0.70,\"winner\":false}";
        Token t = MAPPER.readValue(json, Token.class);
        assertEquals("789", t.getTokenId());
        assertEquals("YES", t.getOutcome());
        assertEquals(0, new BigDecimal("0.70").compareTo(t.getPrice()));
        assertFalse(t.isWinner());
    }
}
