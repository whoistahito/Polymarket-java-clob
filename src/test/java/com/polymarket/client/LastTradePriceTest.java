package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.polymarket.model.BookParams;
import com.polymarket.model.LastTradePriceResult;
import com.polymarket.model.Side;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PMK-011: last-trade-price endpoints carry {price, side}; the bulk one also carries token_id. */
@DisplayName("TC-LTP — last-trade-price exposes side")
class LastTradePriceTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PolymarketClient.Builder().privateKey(PK).clobHost(server.url("/").toString()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("TC-LTP-001 single getLastTradePrice carries price and side")
    void single() throws Exception {
        enqueue("{\"price\":\"0.53\",\"side\":\"BUY\"}");
        LastTradePriceResult r = client.getLastTradePrice("123");
        assertEquals(0, new BigDecimal("0.53").compareTo(r.getPrice()));
        assertEquals(Side.BUY, r.getSide());
    }

    @Test
    @DisplayName("TC-LTP-002 bulk getLastTradesPrices carries token_id, price and side")
    void bulk() throws Exception {
        enqueue("[{\"token_id\":\"123\",\"price\":\"0.53\",\"side\":\"SELL\"}]");
        List<LastTradePriceResult> r =
            client.getLastTradesPrices(List.of(BookParams.builder().tokenId("123").build()));
        assertEquals("123", r.get(0).getTokenId());
        assertEquals(0, new BigDecimal("0.53").compareTo(r.get(0).getPrice()));
        assertEquals(Side.SELL, r.get(0).getSide());
    }
}
