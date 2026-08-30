package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TradeStatus preserves raw values for statuses this release does not know")
class TradeStatusTest {

    @Test
    @DisplayName("TC-TS-001: a known status is recognised and terminal only for CONFIRMED/FAILED")
    void knownStatusesAreRecognised() {
        assertTrue(new TradeStatus("CONFIRMED").is(TradeStatus.Known.CONFIRMED));
        assertTrue(new TradeStatus("CONFIRMED").isTerminal());
        assertTrue(new TradeStatus("FAILED").isTerminal());
        assertFalse(new TradeStatus("MATCHED").isTerminal());
        assertFalse(new TradeStatus("RETRYING").isTerminal());
    }

    @Test
    @DisplayName("TC-TS-003: the documented TRADE_STATUS_ vocabulary is recognised, MINED included")
    void documentedWireVocabularyIsRecognised() {
        // protocol/trades.json tradeStatus.wireValues, from clob-openapi.yaml Trade.status.
        assertTrue(new TradeStatus("TRADE_STATUS_CONFIRMED").is(TradeStatus.Known.CONFIRMED));
        assertTrue(new TradeStatus("TRADE_STATUS_FAILED").is(TradeStatus.Known.FAILED));
        assertTrue(new TradeStatus("TRADE_STATUS_RETRYING").is(TradeStatus.Known.RETRYING));
        assertTrue(new TradeStatus("TRADE_STATUS_MATCHED").is(TradeStatus.Known.MATCHED));
        assertTrue(new TradeStatus("TRADE_STATUS_MINED").is(TradeStatus.Known.MINED));

        assertTrue(new TradeStatus("TRADE_STATUS_CONFIRMED").isTerminal());
        assertTrue(new TradeStatus("TRADE_STATUS_FAILED").isTerminal());
        // trades.json terminalRule: a MINED trade can still be reorganised into RETRYING.
        assertFalse(new TradeStatus("TRADE_STATUS_MINED").isTerminal());
        assertFalse(new TradeStatus("TRADE_STATUS_MATCHED").isTerminal());
        assertFalse(new TradeStatus("TRADE_STATUS_RETRYING").isTerminal());
    }

    @Test
    @DisplayName("TC-TS-002: an unrecognised status keeps its raw text and is not terminal")
    void unknownStatusKeepsRawValue() {
        TradeStatus status = new TradeStatus("SETTLING_NEW_2027");

        assertEquals("SETTLING_NEW_2027", status.raw());
        assertTrue(status.known().isEmpty());
        assertFalse(status.isTerminal());
    }
}
