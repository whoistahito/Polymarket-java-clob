package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.internal.streaming.StreamingGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TC-SD — an event dispatches only to handlers whose filter matches, and one throwing handler
 * cannot stop the rest. Driven end to end through a real (mock) socket since dispatch has no
 * reflection seam in the new capability. */
@DisplayName("TC-SD — Streaming event dispatch")
class StreamingDispatchTest {

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

    /** A server that echoes back whatever text it's told to send once the client sends a frame. */
    private CountDownLatch serveOnFirstFrame(String... framesToSend) throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                for (String frame : framesToSend) ws.send(frame);
                sent.countDown();
            }
        }));
        server.start();
        return sent;
    }

    @Test
    @DisplayName("TC-SD-001 token A callbacks do not receive token B book events")
    void bookCallbacksAreFilteredByToken() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
             "bids":[],"asks":[],"hash":"h"}
            """, """
            {"event_type":"book","asset_id":"tokB","market":"0xm","timestamp":"1",
             "bids":[],"asks":[],"hash":"h"}
            """);
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        List<String> seenByA = new CopyOnWriteArrayList<>();
        List<String> seenByB = new CopyOnWriteArrayList<>();
        CountDownLatch bothSeen = new CountDownLatch(2);

        streaming.onBookUpdate(List.of("tokA"), b -> { seenByA.add(b.assetId()); bothSeen.countDown(); });
        streaming.onBookUpdate(List.of("tokB"), b -> { seenByB.add(b.assetId()); bothSeen.countDown(); });
        streaming.subscribeMarket(List.of("tokA", "tokB"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(bothSeen.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("tokA"), seenByA);
        assertEquals(List.of("tokB"), seenByB);
    }

    @Test
    @DisplayName("TC-SD-002 price-change batches are filtered per entry asset")
    void priceChangeFilteredPerEntry() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"event_type":"price_change","market":"0xm","timestamp":"1",
             "price_changes":[{"asset_id":"tokA","price":"0.5","size":"200","side":"BUY"}]}
            """);
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        List<PriceChangeEvent> seenByA = new CopyOnWriteArrayList<>();
        List<PriceChangeEvent> seenByB = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        streaming.onPriceChange(List.of("tokA"), e -> { seenByA.add(e); got.countDown(); });
        streaming.onPriceChange(List.of("tokB"), seenByB::add);
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(1, seenByA.size());
        assertTrue(seenByB.isEmpty(), "a batch touching only tokA must not reach a tokB handler");
    }

    @Test
    @DisplayName("TC-SD-003 an empty filter receives every asset")
    void emptyFilterReceivesEverything() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"event_type":"last_trade_price","asset_id":"tokA","market":"0xm","price":"0.4","timestamp":"1"}
            """);
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        List<LastTradePriceEvent> seen = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        streaming.onLastTradePrice(List.of(), e -> { seen.add(e); got.countDown(); });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(1, seen.size());
    }

    @Test
    @DisplayName("TC-SD-004 one throwing callback does not stop the others")
    void oneThrowingCallbackDoesNotStopOthers() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"event_type":"book","asset_id":"tokA","market":"0xm","timestamp":"1",
             "bids":[],"asks":[],"hash":"h"}
            """);
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        streaming.onBookUpdate(List.of("tokA"), b -> { throw new IllegalStateException("boom"); });
        streaming.onBookUpdate(List.of("tokA"), b -> { seen.add(b.assetId()); got.countDown(); });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("tokA"), seen);
    }

    @Test
    @DisplayName("TC-SD-005 tick-size-change callback receives the parsed event")
    void tickSizeChangeDelivered() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"event_type":"tick_size_change","asset_id":"tokA","market":"0xm",
             "old_tick_size":"0.01","new_tick_size":"0.001","timestamp":"1"}
            """);
        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        List<TickSizeChangeEvent> seen = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        streaming.onTickSizeChange(List.of("tokA"), e -> { seen.add(e); got.countDown(); });
        streaming.subscribeMarket(List.of("tokA"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(new java.math.BigDecimal("0.01"), seen.get(0).oldTickSize());
        assertEquals(new java.math.BigDecimal("0.001"), seen.get(0).newTickSize());
    }

    @Test
    @DisplayName("TC-SD-006 user order and trade callbacks are filtered by market condition ID")
    void userCallbacksFilteredByMarket() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"event_type":"order","id":"0x1","market":"0xm1","asset_id":"tokA","side":"BUY",
             "size_matched":"1","type":"UPDATE"}
            """);
        server.enqueue(new MockResponse()); // unused; only one connection is made in this test

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway,
                SigningAuthority.apiOnly(new ApiCredentials("key", "secret", "pass")));
        List<OrderEvent> forMarket1 = new ArrayList<>();
        List<OrderEvent> forMarket2 = new ArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        streaming.onOrder(List.of("0xm1"), o -> { forMarket1.add(o); got.countDown(); });
        streaming.onOrder(List.of("0xm2"), forMarket2::add);
        streaming.subscribeUser(List.of("0xm1"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(1, forMarket1.size());
        assertTrue(forMarket2.isEmpty());
    }

    @Test
    @DisplayName("TC-SD-007 the pinned custom market events reach their own typed handlers")
    void customMarketEventsReachTypedHandlers() throws Exception {
        String bestBidAsk = StreamProtocol.at("marketChannel", "events", "best_bid_ask").toString();
        String newMarket = StreamProtocol.at("marketChannel", "events", "new_market").toString();
        String resolved = StreamProtocol.at("marketChannel", "events", "market_resolved").toString();
        String assetId = StreamProtocol.at("marketChannel", "events", "best_bid_ask").get("asset_id").asText();
        serveOnFirstFrame(bestBidAsk, newMarket, resolved);

        gateway = StreamingGateway.builder().wsBase(wsBase()).build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        CountDownLatch got = new CountDownLatch(3);
        List<BestBidAskEvent> tops = new CopyOnWriteArrayList<>();
        List<NewMarketEvent> created = new CopyOnWriteArrayList<>();
        List<MarketResolvedEvent> settled = new CopyOnWriteArrayList<>();
        streaming.onBestBidAsk(List.of(assetId), e -> { tops.add(e); got.countDown(); });
        streaming.onNewMarket(List.of(assetId), e -> { created.add(e); got.countDown(); });
        streaming.onMarketResolved(List.of(assetId), e -> { settled.add(e); got.countDown(); });
        streaming.enableCustomMarketEvents();
        streaming.subscribeMarket(List.of(assetId));

        assertTrue(got.await(10, TimeUnit.SECONDS), "all three custom events must be dispatched");
        assertEquals(new java.math.BigDecimal("0.01"), tops.get(0).spread());
        assertEquals("Will the US confirm that aliens exist before 2027?", created.get(0).question());
        assertEquals(List.of("Yes", "No"), created.get(0).outcomes());
        assertEquals("Yes", settled.get(0).winningOutcome());
        assertEquals(assetId, settled.get(0).winningAssetId());
    }

    @Test
    @DisplayName("TC-SD-008 custom market events are not delivered to a channel that did not request them")
    void customMarketEventsNeedTheDocumentedFlag() {
        gateway = StreamingGateway.builder().wsBase("wss://127.0.0.1:1").build();
        streaming = new Streaming(gateway, SigningAuthority.none());
        streaming.subscribeMarket(List.of("tokA"));

        assertEquals(false, streaming.customMarketEventsEnabled());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                streaming::enableCustomMarketEvents, "the flag rides the initial frame, so it cannot arrive later");
    }
}
