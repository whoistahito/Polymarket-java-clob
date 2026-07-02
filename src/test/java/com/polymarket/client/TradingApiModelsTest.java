package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the trading API parameter and response model builders:
 * {@link HeartbeatResponse}, {@link OpenOrderParams}, {@link TradeParams}.
 */
@DisplayName("TC-TAM — Trading API model builder tests")
class TradingApiModelsTest {

    // ------------------------------------------------------------------ //
    // HeartbeatResponse                                                    //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-M3-001 HeartbeatResponse builder sets heartbeatId")
    void heartbeatResponseBuilder() {
        HeartbeatResponse r = HeartbeatResponse.builder()
            .heartbeatId("hb-123")
            .build();
        assertEquals("hb-123", r.getHeartbeatId());
        assertNull(r.getError());
    }

    @Test
    @DisplayName("TC-M3-002 HeartbeatResponse builder sets error")
    void heartbeatResponseError() {
        HeartbeatResponse r = HeartbeatResponse.builder()
            .heartbeatId("hb-456")
            .error("timeout")
            .build();
        assertEquals("timeout", r.getError());
    }

    @Test
    @DisplayName("TC-M3-003 HeartbeatResponse no-arg constructor")
    void heartbeatResponseNoArg() {
        HeartbeatResponse r = new HeartbeatResponse();
        assertNull(r.getHeartbeatId());
        assertNull(r.getError());
    }

    // ------------------------------------------------------------------ //
    // OpenOrderParams                                                      //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-M3-010 OpenOrderParams builder with all fields")
    void openOrderParamsAllFields() {
        OpenOrderParams p = OpenOrderParams.builder()
            .id("order-1")
            .market("0xabc")
            .assetId("tok1")
            .build();
        assertEquals("order-1", p.getId());
        assertEquals("0xabc",   p.getMarket());
        assertEquals("tok1",    p.getAssetId());
    }

    @Test
    @DisplayName("TC-M3-011 OpenOrderParams builder allows all-null")
    void openOrderParamsAllNull() {
        OpenOrderParams p = OpenOrderParams.builder().build();
        assertNull(p.getId());
        assertNull(p.getMarket());
    }

    @Test
    @DisplayName("TC-M3-012 OpenOrderParams is immutable (@Value)")
    void openOrderParamsImmutable() {
        OpenOrderParams p = OpenOrderParams.builder().id("x").build();
        // @Value = no setters; this line should not compile if we tried — just verify equality
        OpenOrderParams p2 = OpenOrderParams.builder().id("x").build();
        assertEquals(p, p2);
    }

    // ------------------------------------------------------------------ //
    // TradeParams                                                          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-M3-020 TradeParams builder with all fields")
    void tradeParamsAllFields() {
        TradeParams p = TradeParams.builder()
            .id("trade-1")
            .makerAddress("0xMaker")
            .market("0xMarket")
            .assetId("tok2")
            .before("2024-01-01")
            .after("2023-01-01")
            .build();
        assertEquals("trade-1",    p.getId());
        assertEquals("0xMaker",    p.getMakerAddress());
        assertEquals("0xMarket",   p.getMarket());
        assertEquals("tok2",       p.getAssetId());
        assertEquals("2024-01-01", p.getBefore());
        assertEquals("2023-01-01", p.getAfter());
    }

    @Test
    @DisplayName("TC-M3-021 TradeParams builder allows all-null")
    void tradeParamsAllNull() {
        TradeParams p = TradeParams.builder().build();
        assertNull(p.getId());
        assertNull(p.getMakerAddress());
        assertNull(p.getMarket());
        assertNull(p.getAssetId());
        assertNull(p.getBefore());
        assertNull(p.getAfter());
    }

    @Test
    @DisplayName("TC-M3-022 TradeParams equality")
    void tradeParamsEquality() {
        TradeParams a = TradeParams.builder().id("t").market("m").build();
        TradeParams b = TradeParams.builder().id("t").market("m").build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
