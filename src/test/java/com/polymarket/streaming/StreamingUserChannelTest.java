package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.internal.streaming.StreamingGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TC-SU — the authenticated user channel: wire shape (issue #22) and documented event fields. */
@DisplayName("TC-SU — Streaming user channel")
class StreamingUserChannelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ApiCredentials CREDS = new ApiCredentials("key", "c2VjcmV0", "pass");
    private static final String ACCOUNT_SIGNER = "0x" + "a".repeat(40);

    private MockWebServer server;
    private StreamingGateway gateway;
    private Streaming streaming;

    @AfterEach
    void tearDown() throws Exception {
        if (streaming != null) streaming.close();
        if (gateway != null) gateway.close();
        if (server != null) {
            Thread.sleep(100);
            try {
                server.shutdown();
            } catch (Exception ignored) {
                // teardown only
            }
        }
    }

    private String wsBase() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    private List<String> startCapturingServer() throws Exception {
        List<String> frames = new CopyOnWriteArrayList<>();
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { frames.add(text); }
        }));
        server.start();
        return frames;
    }

    @Test
    @DisplayName("TC-SU-001 the initial user frame carries the nested auth object, no wallet address")
    void initialFrameCarriesNestedAuth() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.apiCredentials(CREDS, ACCOUNT_SIGNER));

        streaming.subscribeUser(List.of("0xm1"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size());
        JsonNode frame = MAPPER.readTree(frames.get(0));
        assertEquals("user", frame.get("type").asText());
        assertEquals("subscribe", frame.get("operation").asText());
        assertTrue(frame.get("initial_dump").asBoolean());
        JsonNode auth = frame.get("auth");
        assertEquals("key", auth.get("apiKey").asText());
        assertEquals("c2VjcmV0", auth.get("secret").asText());
        assertEquals("pass", auth.get("passphrase").asText());
        assertFalse(frame.has("signature"), "no HMAC signature — the L2 shape is apiKey/secret/passphrase only");
        assertFalse(frame.has("timestamp"), "the frame itself carries no timestamp");
        assertFalse(frame.has("walletAddress"), "no unused wallet-address field");
    }

    @Test
    @DisplayName("TC-SU-002 a dynamic subscribe update does not repeat the credentials")
    void dynamicUpdateDoesNotRepeatCredentials() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.apiCredentials(CREDS, ACCOUNT_SIGNER));

        streaming.subscribeUser(List.of("0xm1"));
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);
        streaming.subscribeUser(List.of("0xm2"));
        for (int i = 0; i < 50 && frames.size() < 2; i++) Thread.sleep(50);

        assertEquals(2, frames.size(), frames.toString());
        JsonNode update = MAPPER.readTree(frames.get(1));
        assertEquals("subscribe", update.get("operation").asText());
        assertFalse(update.has("initial_dump"));
        assertFalse(update.has("auth"), "a dynamic update on an authenticated socket must not repeat secrets");
        assertEquals(1, update.get("markets").size());
        assertEquals("0xm2", update.get("markets").get(0).asText());
    }

    @Test
    @DisplayName("TC-SU-003 an empty user market filter still opens and authenticates the channel")
    void emptyFilterStillAuthenticates() throws Exception {
        List<String> frames = startCapturingServer();
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.apiCredentials(CREDS, ACCOUNT_SIGNER));

        // subscribeUser with an empty list still opens the channel (empty filter == "every market").
        streaming.subscribeUser(List.of());
        for (int i = 0; i < 50 && frames.isEmpty(); i++) Thread.sleep(50);

        assertEquals(1, frames.size());
        JsonNode frame = MAPPER.readTree(frames.get(0));
        assertTrue(frame.has("auth"));
        assertEquals(0, frame.get("markets").size());
    }

    @Test
    @DisplayName("TC-SU-004 the complete documented order fixture preserves every field")
    void orderPreservesDocumentedFields() throws Exception {
        String orderJson = """
            {
              "event_type": "order",
              "id": "0xorder1",
              "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
              "market": "0x5f65177b",
              "asset_id": "71321045",
              "side": "SELL",
              "original_size": "7",
              "size_matched": "4",
              "price": "0.52",
              "type": "UPDATE",
              "order_type": "GTC",
              "status": "LIVE",
              "associate_trades": ["t1", "t2"],
              "expiration": "1735689600",
              "created_at": "1700000000",
              "outcome": "Up",
              "maker_address": "0x1234567890123456789012345678901234567890",
              "timestamp": "1757908892351"
            }
            """;
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.send(orderJson); }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.apiCredentials(CREDS, ACCOUNT_SIGNER));
        CopyOnWriteArrayList<OrderEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);
        streaming.onOrder(List.of(), o -> { events.add(o); got.countDown(); });
        streaming.subscribeUser(List.of("0x5f65177b"));

        assertTrue(got.await(10, TimeUnit.SECONDS));
        OrderEvent order = events.get(0);
        assertEquals("0xorder1", order.id());
        assertEquals("0x5f65177b", order.market());
        assertEquals("71321045", order.assetId());
        assertEquals("SELL", order.side());
        assertEquals(new BigDecimal("0.52"), order.price());
        assertEquals("UPDATE", order.type());
        assertEquals(new BigDecimal("7"), order.originalSize().orElseThrow());
        assertEquals(new BigDecimal("4"), order.sizeMatched().orElseThrow());
        assertEquals("LIVE", order.status().orElseThrow());
        assertEquals(List.of("t1", "t2"), order.associatedTrades());
        assertEquals("1735689600", order.expiration().orElseThrow());
        assertEquals("1700000000", order.createdAt().orElseThrow());
        assertEquals("GTC", order.orderType().orElseThrow());
        assertEquals("Up", order.outcome().orElseThrow());
        assertEquals("0x1234567890123456789012345678901234567890", order.makerAddress().orElseThrow());
        assertEquals("1757908892351", order.timestamp().orElseThrow());
        assertEquals("f4f247b7-4ac7-ff29-a152-04fda0a8755a", order.owner().orElseThrow());
    }

    @Test
    @DisplayName("TC-SU-005 the complete documented trade fixture preserves every field")
    void tradePreservesDocumentedFields() throws Exception {
        String tradeJson = """
            {
              "event_type": "trade",
              "id": "t1",
              "market": "0xeee",
              "asset_id": "tok9",
              "side": "BUY",
              "size": "50",
              "price": "0.7",
              "status": "MATCHED",
              "type": "trade",
              "last_update": "100",
              "matchtime": "200",
              "timestamp": "300",
              "outcome": "Yes",
              "owner": "owner-1",
              "trade_owner": "trade-owner-1",
              "taker_order_id": "taker-1",
              "fee_rate_bps": "0",
              "transaction_hash": "0xabc",
              "trader_side": "TAKER",
              "maker_orders": [{
                "asset_id": "tok9", "matched_amount": "50", "order_id": "maker-1", "outcome": "Yes",
                "side": "SELL", "owner": "maker-owner", "maker_address": "0xmaker", "price": "0.7",
                "fee_rate_bps": "0"
              }]
            }
            """;
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.send(tradeJson); }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.apiCredentials(CREDS, ACCOUNT_SIGNER));
        CopyOnWriteArrayList<TradeEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);
        streaming.onTrade(List.of(), t -> { events.add(t); got.countDown(); });
        streaming.subscribeUser(List.of("0xeee"));

        assertTrue(got.await(10, TimeUnit.SECONDS));
        TradeEvent trade = events.get(0);
        assertEquals("t1", trade.id());
        assertEquals(new BigDecimal("50"), trade.size());
        assertEquals(new BigDecimal("0.7"), trade.price());
        assertEquals("MATCHED", trade.status());
        assertEquals("200", trade.matchTime().orElseThrow());
        assertEquals("100", trade.lastUpdate().orElseThrow());
        assertEquals("0xabc", trade.transactionHash().orElseThrow());
        assertEquals("TAKER", trade.traderSide().orElseThrow());
        assertEquals(1, trade.makerOrders().size());
        MakerOrder maker = trade.makerOrders().get(0);
        assertEquals("maker-1", maker.orderId());
        assertEquals(new BigDecimal("50"), maker.matchedAmount().orElseThrow());
        assertEquals("0xmaker", maker.makerAddress().orElseThrow());
    }

    @Test
    @DisplayName("TC-SU-006 an undocumented event_type is ignored without breaking dispatch")
    void unknownEventTypeIsIgnored() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                ws.send("""
                    {"event_type":"best_bid_ask","asset_id":"tokA","market":"0xm",
                     "best_bid":"0.4","best_ask":"0.6","timestamp":"1"}
                    """);
                ws.send("""
                    {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
                     "bids":[],"asks":[],"hash":"h"}
                    """);
            }
        }));
        server.start();

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        CountDownLatch got = new CountDownLatch(1);
        streaming.onBookUpdate(List.of("tokA"), b -> got.countDown());
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(got.await(10, TimeUnit.SECONDS), "the book event after the unknown one must still arrive");
    }
}
