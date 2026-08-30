package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.internal.streaming.RtdsGateway;
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

/** TC-RD — an RTDS event dispatches only to handlers whose filter matches, distinctly typed per
 * topic/type, and one throwing handler cannot stop the rest. */
@DisplayName("TC-RD — Rtds event dispatch")
class RtdsDispatchTest {

    private MockWebServer server;
    private RtdsGateway gateway;
    private Rtds rtds;

    @AfterEach
    void tearDown() throws Exception {
        if (rtds != null) rtds.close();
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

    private String wsUrl() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

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
    @DisplayName("TC-RD-001 Binance and Chainlink prices are distinct typed events")
    void binanceAndChainlinkAreDistinctEvents() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"topic":"crypto_prices","type":"update","timestamp":1700000000000,
             "payload":{"symbol":"btcusdt","timestamp":1700000000000,"value":67234.5}}
            """, """
            {"topic":"crypto_prices_chainlink","type":"update","timestamp":1700000000000,
             "payload":{"symbol":"btc/usd","timestamp":1700000000000,"value":67200.1}}
            """);
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<BinancePriceEvent> binance = new CopyOnWriteArrayList<>();
        List<ChainlinkPriceEvent> chainlink = new CopyOnWriteArrayList<>();
        CountDownLatch bothSeen = new CountDownLatch(2);

        rtds.onBinancePrice(List.of(), e -> { binance.add(e); bothSeen.countDown(); });
        rtds.onChainlinkPrice(List.of(), e -> { chainlink.add(e); bothSeen.countDown(); });
        rtds.subscribeBinancePrices(List.of("btcusdt"));
        rtds.subscribeChainlinkPrices(List.of("btc/usd"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(bothSeen.await(10, TimeUnit.SECONDS));
        assertEquals("btcusdt", binance.get(0).symbol());
        assertEquals(new BigDecimal("67234.5"), binance.get(0).value());
        assertEquals("btc/usd", chainlink.get(0).symbol());
        assertEquals(new BigDecimal("67200.1"), chainlink.get(0).value());
    }

    @Test
    @DisplayName("TC-RD-002 Binance price callbacks are filtered by symbol")
    void binancePriceFilteredBySymbol() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"topic":"crypto_prices","type":"update","timestamp":1,
             "payload":{"symbol":"btcusdt","timestamp":1,"value":1}}
            """, """
            {"topic":"crypto_prices","type":"update","timestamp":1,
             "payload":{"symbol":"ethusdt","timestamp":1,"value":2}}
            """);
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<String> seenByBtc = new CopyOnWriteArrayList<>();
        List<String> seenByEth = new CopyOnWriteArrayList<>();
        CountDownLatch bothSeen = new CountDownLatch(2);

        rtds.onBinancePrice(List.of("btcusdt"), e -> { seenByBtc.add(e.symbol()); bothSeen.countDown(); });
        rtds.onBinancePrice(List.of("ethusdt"), e -> { seenByEth.add(e.symbol()); bothSeen.countDown(); });
        rtds.subscribeBinancePrices(List.of("btcusdt", "ethusdt"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(bothSeen.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("btcusdt"), seenByBtc);
        assertEquals(List.of("ethusdt"), seenByEth);
    }

    @Test
    @DisplayName("TC-RD-003 comment-created, comment-removed, reaction-created, and reaction-removed "
            + "are distinct typed events")
    void commentEventsAreDistinctTypedEvents() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"topic":"comments","type":"comment_created","timestamp":1,
             "payload":{"id":"1","body":"hi","parentEntityType":"Event","parentEntityID":18396,
             "userAddress":"0xabc"}}
            """, """
            {"topic":"comments","type":"comment_removed","timestamp":1,
             "payload":{"id":"1","userAddress":"0xabc"}}
            """, """
            {"topic":"comments","type":"reaction_created","timestamp":1,
             "payload":{"id":"r1","commentID":1,"reactionType":"HEART","userAddress":"0xabc"}}
            """, """
            {"topic":"comments","type":"reaction_removed","timestamp":1,
             "payload":{"id":"r1","commentID":1,"reactionType":"HEART"}}
            """);
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<CommentCreatedEvent> created = new CopyOnWriteArrayList<>();
        List<CommentRemovedEvent> removed = new CopyOnWriteArrayList<>();
        List<ReactionCreatedEvent> reacted = new CopyOnWriteArrayList<>();
        List<ReactionRemovedEvent> unreacted = new CopyOnWriteArrayList<>();
        CountDownLatch allFour = new CountDownLatch(4);

        rtds.onCommentCreated(e -> { created.add(e); allFour.countDown(); });
        rtds.onCommentRemoved(e -> { removed.add(e); allFour.countDown(); });
        rtds.onReactionCreated(e -> { reacted.add(e); allFour.countDown(); });
        rtds.onReactionRemoved(e -> { unreacted.add(e); allFour.countDown(); });
        rtds.subscribeComments(CommentEventType.COMMENT_CREATED);
        rtds.subscribeComments(CommentEventType.COMMENT_REMOVED);
        rtds.subscribeComments(CommentEventType.REACTION_CREATED);
        rtds.subscribeComments(CommentEventType.REACTION_REMOVED);

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(allFour.await(10, TimeUnit.SECONDS));
        assertEquals("hi", created.get(0).body().orElseThrow());
        assertEquals(RtdsEntityType.EVENT, created.get(0).parentEntityType().orElseThrow());
        assertEquals(18396L, created.get(0).parentEntityId().orElseThrow());
        assertEquals("1", removed.get(0).id());
        assertEquals("HEART", reacted.get(0).reactionType().orElseThrow());
        assertEquals(1L, reacted.get(0).commentId().orElseThrow());
        assertEquals("HEART", unreacted.get(0).reactionType().orElseThrow());
    }

    @Test
    @DisplayName("TC-RD-006 the pinned RTDS frames keep their observation time and every mapped field")
    void pinnedFramesPreserveObservationTimeAndDocumentedFields() throws Exception {
        String price = StreamProtocol.at("rtds", "events", "crypto_prices").toString();
        String comment = StreamProtocol.at("rtds", "events", "comment_created").toString();
        String reaction = StreamProtocol.at("rtds", "events", "reaction_created").toString();
        long envelopeTime = StreamProtocol.at("rtds", "events", "crypto_prices", "timestamp").asLong();
        CountDownLatch sent = serveOnFirstFrame(price, comment, reaction);

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<BinancePriceEvent> prices = new CopyOnWriteArrayList<>();
        List<CommentCreatedEvent> comments = new CopyOnWriteArrayList<>();
        List<ReactionCreatedEvent> reactions = new CopyOnWriteArrayList<>();
        CountDownLatch allThree = new CountDownLatch(3);

        rtds.onBinancePrice(List.of(), e -> { prices.add(e); allThree.countDown(); });
        rtds.onCommentCreated(e -> { comments.add(e); allThree.countDown(); });
        rtds.onReactionCreated(e -> { reactions.add(e); allThree.countDown(); });
        rtds.subscribeBinancePrices(List.of("btcusdt"));
        rtds.subscribeComments(CommentEventType.COMMENT_CREATED);
        rtds.subscribeComments(CommentEventType.REACTION_CREATED);

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(allThree.await(10, TimeUnit.SECONDS));

        // The envelope timestamp is when RTDS observed the event; the payload timestamp is when the
        // source produced it. Dropping the envelope one loses the only stream-side ordering fact.
        assertEquals(envelopeTime, prices.get(0).observedAt());
        assertEquals(envelopeTime, comments.get(0).observedAt());
        assertEquals(envelopeTime, reactions.get(0).observedAt());
        assertEquals(1782753357213L, prices.get(0).timestamp(), "the payload time survives too");

        assertEquals("salted.caramel", comments.get(0).profile().orElseThrow().name());
        assertEquals("0xce533188d53a16ed580fd5121dedf166d3482677",
                reactions.get(0).userAddress().orElseThrow());
    }

    @Test
    @DisplayName("TC-RD-007 a reaction keeps the nested profile RTDS documents for it")
    void reactionsKeepTheirNestedProfile() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"topic":"comments","type":"reaction_created","timestamp":7,
             "payload":{"id":"r1","commentID":1,"reactionType":"HEART","userAddress":"0xabc",
             "profile":{"baseAddress":"0xabc","displayUsernamePublic":true,"name":"n",
             "proxyWallet":"0xdef","pseudonym":"p"}}}
            """);
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<ReactionCreatedEvent> seen = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        rtds.onReactionCreated(e -> { seen.add(e); got.countDown(); });
        rtds.subscribeComments(CommentEventType.REACTION_CREATED);

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals("n", seen.get(0).profile().orElseThrow().name());
        assertEquals(7L, seen.get(0).observedAt());
    }

    @Test
    @DisplayName("TC-RD-004 one throwing callback does not stop the others")
    void oneThrowingCallbackDoesNotStopOthers() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"topic":"crypto_prices","type":"update","timestamp":1,
             "payload":{"symbol":"btcusdt","timestamp":1,"value":1}}
            """);
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        rtds.onBinancePrice(List.of(), e -> { throw new IllegalStateException("boom"); });
        rtds.onBinancePrice(List.of(), e -> { seen.add(e.symbol()); got.countDown(); });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("btcusdt"), seen);
    }

    @Test
    @DisplayName("TC-RD-005 official entity filters decode from the comment payload")
    void entityFilterDecodesFromPayload() throws Exception {
        CountDownLatch sent = serveOnFirstFrame("""
            {"topic":"comments","type":"comment_created","timestamp":1,
             "payload":{"id":"1","parentEntityType":"Market","parentEntityID":42}}
            """);
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        List<CommentCreatedEvent> seen = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        rtds.onCommentCreated(e -> { seen.add(e); got.countDown(); });
        rtds.subscribeComments(CommentEventType.COMMENT_CREATED, RtdsEntityType.MARKET, 42);

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(got.await(10, TimeUnit.SECONDS));
        assertEquals(RtdsEntityType.MARKET, seen.get(0).parentEntityType().orElseThrow());
        assertEquals(42L, seen.get(0).parentEntityId().orElseThrow());
    }
}
