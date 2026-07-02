package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.model.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for PolymarketClient typed-params overloads, postOnly/deferExec
 * convenience overloads, and L2 auth guard validation.
 */
@DisplayName("TC-PC-TP — PolymarketClient typed-params and convenience overload tests")
class PolymarketClientTypedParamsTest {

    private static final String PK = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private PolymarketClient clientNoAuth;
    private PolymarketClient clientL2;

    @BeforeEach
    void setUp() {
        // Client with no API creds (only wallet key) — L2 API calls should fail with auth guard
        clientNoAuth = new PolymarketClient.Builder()
            .privateKey(PK)
            .chainId(137)
            .build();

        ApiKeyCreds creds = new ApiKeyCreds("k", "c2VjcmV0", "pass");

        clientL2 = new PolymarketClient.Builder()
            .privateKey(PK)
            .chainId(137)
            .apiCreds(creds)
            .funderAddress("0x1234567890123456789012345678901234567890")
            .build();
    }

    // ------------------------------------------------------------------ //
    // Typed params overloads — guard checks (no network)                  //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-PC3-001 getOpenOrders(OpenOrderParams) requires L2 auth")
    void getOpenOrdersTypedParamsRequiresAuth() {
        OpenOrderParams params = OpenOrderParams.builder().market("0xabc").build();
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.getOpenOrders(params));
    }

    @Test
    @DisplayName("TC-PC3-002 getOpenOrders(null OpenOrderParams) falls through to raw overload")
    void getOpenOrdersNullTypedParams() {
        // Should throw L2 auth guard, not NPE
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.getOpenOrders((OpenOrderParams) null));
    }

    @Test
    @DisplayName("TC-PC3-003 getTrades() no-arg requires L2 auth")
    void getTradesNoArgRequiresAuth() {
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.getTrades());
    }

    @Test
    @DisplayName("TC-PC3-004 getTrades(TradeParams) requires L2 auth")
    void getTradesTypedParamsRequiresAuth() {
        TradeParams params = TradeParams.builder().assetId("tok1").build();
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.getTrades(params));
    }

    @Test
    @DisplayName("TC-PC3-005 getTrades(TradeParams) null falls through to raw overload")
    void getTradesNullTypedParams() {
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.getTrades((TradeParams) null));
    }

    // ------------------------------------------------------------------ //
    // postOrder / postOrders convenience overloads — guard checks         //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-PC3-010 postOrder(SignedOrder, OrderType, postOnly, deferExec) requires L2 auth")
    void postOrderConvenienceRequiresAuth() {
        // requireL2Auth() fires before the SignedOrder is touched
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.postOrder(SignedOrder.builder().build(), OrderType.GTC, false, false));
    }

    @Test
    @DisplayName("TC-PC3-011 postOrders(List<SignedOrder>, OrderType, postOnly, deferExec) requires L2 auth")
    void postOrdersConvenienceRequiresAuth() {
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.postOrders(List.of(), OrderType.GTC, false, false));
    }

    @Test
    @DisplayName("TC-PC3-012 postOrder(SignedOrder,...) requires L2 auth for FOK type")
    void postOrderConvenienceL2AuthFok() {
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.postOrder(SignedOrder.builder().build(), OrderType.FOK, false, false));
    }

    // ------------------------------------------------------------------ //
    // HeartbeatResponse                                                    //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-PC3-030 postHeartbeat requires L2 auth")
    void postHeartbeatRequiresAuth() {
        assertThrows(IllegalStateException.class,
            () -> clientNoAuth.postHeartbeat("hb-1"));
    }
}
