package com.polymarket.rtds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mirrors the Rust SDK {@code rtds::types::response} parsing tests. */
class RtdsMessageTest {

    @Test
    @DisplayName("TC-RTDS-020 parse crypto_prices message")
    void parseCryptoPrice() {
        String json = """
            {"topic":"crypto_prices","type":"update","timestamp":1753314064237,
             "payload":{"symbol":"solusdt","timestamp":1753314064213,"value":189.55}}""";
        List<RtdsMessage> msgs = RtdsMessage.parse(json);
        assertEquals(1, msgs.size());
        RtdsMessage m = msgs.get(0);
        assertEquals("crypto_prices", m.topic());
        assertEquals("update", m.type());

        RtdsMessage.CryptoPrice price = m.asCryptoPrice().orElseThrow();
        assertEquals("solusdt", price.symbol());
        assertEquals(0, new BigDecimal("189.55").compareTo(price.value()));
        // Topic gating: not a chainlink/comment payload
        assertTrue(m.asChainlinkPrice().isEmpty());
        assertTrue(m.asComment().isEmpty());
    }

    @Test
    @DisplayName("TC-RTDS-021 parse chainlink message")
    void parseChainlinkPrice() {
        String json = """
            {"topic":"crypto_prices_chainlink","type":"update","timestamp":1753314064237,
             "payload":{"symbol":"eth/usd","timestamp":1753314064213,"value":3456.78}}""";
        RtdsMessage m = RtdsMessage.parse(json).get(0);
        RtdsMessage.ChainlinkPrice price = m.asChainlinkPrice().orElseThrow();
        assertEquals("eth/usd", price.symbol());
        assertEquals(0, new BigDecimal("3456.78").compareTo(price.value()));
        assertTrue(m.asCryptoPrice().isEmpty());
    }

    @Test
    @DisplayName("TC-RTDS-022 parse comment message")
    void parseComment() {
        String json = """
            {"topic":"comments","type":"comment_created","timestamp":1753454975808,
             "payload":{"body":"Test comment","createdAt":"2025-07-25T14:49:35.801298Z",
               "id":"1763355","parentCommentID":"1763325","parentEntityID":18396,
               "parentEntityType":"Event",
               "profile":{"baseAddress":"0xce53","displayUsernamePublic":true,
                 "name":"salted.caramel","proxyWallet":"0x4ca7","pseudonym":"Adored-Disparity"},
               "reactionCount":0,"replyAddress":"0x0bda","reportCount":0,
               "userAddress":"0xce53"}}""";
        RtdsMessage m = RtdsMessage.parse(json).get(0);
        assertEquals("comments", m.topic());
        RtdsMessage.Comment c = m.asComment().orElseThrow();
        assertEquals("1763355", c.id());
        assertEquals("Test comment", c.body());
        assertEquals(18396, c.parentEntityId());
        assertEquals("salted.caramel", c.profile().name());
        assertTrue(c.profile().displayUsernamePublic());
    }

    @Test
    @DisplayName("TC-RTDS-023 parse array of messages")
    void parseArray() {
        String json = """
            [{"topic":"crypto_prices","type":"update","timestamp":1,
              "payload":{"symbol":"btcusdt","timestamp":2,"value":67234.50}}]""";
        List<RtdsMessage> msgs = RtdsMessage.parse(json);
        assertEquals(1, msgs.size());
        assertEquals("crypto_prices", msgs.get(0).topic());
    }

    @Test
    @DisplayName("TC-RTDS-024 empty / whitespace / null frames yield no messages")
    void parseEmpty() {
        assertTrue(RtdsMessage.parse("").isEmpty());
        assertTrue(RtdsMessage.parse("   \n\t  ").isEmpty());
        assertTrue(RtdsMessage.parse(null).isEmpty());
    }

    @Test
    @DisplayName("TC-RTDS-025 as* returns empty when payload missing")
    void asMissingPayload() {
        RtdsMessage m = RtdsMessage.parse(
            "{\"topic\":\"crypto_prices\",\"type\":\"update\",\"timestamp\":1}").get(0);
        assertTrue(m.asCryptoPrice().isEmpty());
    }
}
