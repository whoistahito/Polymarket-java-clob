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
    @DisplayName("TC-TS-002: an unrecognised status keeps its raw text and is not terminal")
    void unknownStatusKeepsRawValue() {
        TradeStatus status = new TradeStatus("SETTLING_NEW_2027");

        assertEquals("SETTLING_NEW_2027", status.raw());
        assertTrue(status.known().isEmpty());
        assertFalse(status.isTerminal());
    }
}
