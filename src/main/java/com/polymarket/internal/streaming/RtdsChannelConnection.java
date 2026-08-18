package com.polymarket.internal.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymarket.streaming.CommentSubscription;
import com.polymarket.streaming.RtdsConnection;
import com.polymarket.streaming.RtdsEventSink;
import com.polymarket.streaming.RtdsSubscriptions;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one live RTDS socket, reconnecting transparently. Ported reconnect/backoff/heartbeat
 * algorithm from the CLOB {@code ChannelConnection}; the wire envelope and topics are RTDS's own.
 */
final class RtdsChannelConnection implements RtdsConnection {

    private static final Logger log = LoggerFactory.getLogger(RtdsChannelConnection.class);
    private static final String PING_FRAME = "PING";
    private static final String TOPIC_BINANCE = "crypto_prices";
    private static final String TOPIC_CHAINLINK = "crypto_prices_chainlink";
    private static final String TOPIC_COMMENTS = "comments";

    private final OkHttpClient okHttp;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper;
    private final RtdsEventMapper eventMapper;
    private final String url;
    private final Supplier<RtdsSubscriptions> stateSupplier;
    private final RtdsEventSink sink;

    private final long pingIntervalMs;
    private final long reconnectDelayMs;
    private final long maxReconnectDelayMs;
    private final long stableConnectionMs;
    private final int maxReconnectAttempts;

    private volatile WebSocket socket;
    private volatile boolean closed;
    private final AtomicLong generation = new AtomicLong(0);
    private final AtomicInteger attempt = new AtomicInteger(0);
    private final AtomicLong openedAtMs = new AtomicLong(0);
    private volatile ScheduledFuture<?> heartbeat;

    RtdsChannelConnection(OkHttpClient okHttp, ScheduledExecutorService scheduler, ObjectMapper mapper, String url,
            Supplier<RtdsSubscriptions> stateSupplier, RtdsEventSink sink, long pingIntervalMs,
            long reconnectDelayMs, long maxReconnectDelayMs, long stableConnectionMs, int maxReconnectAttempts) {
        this.okHttp = okHttp;
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.eventMapper = new RtdsEventMapper(mapper);
        this.url = url;
        this.stateSupplier = stateSupplier;
        this.sink = sink;
        this.pingIntervalMs = pingIntervalMs;
        this.reconnectDelayMs = reconnectDelayMs;
        this.maxReconnectDelayMs = maxReconnectDelayMs;
        this.stableConnectionMs = stableConnectionMs;
        this.maxReconnectAttempts = maxReconnectAttempts;
        open();
    }

    private void open() {
        Request request = new Request.Builder().url(url).build();
        socket = okHttp.newWebSocket(request, new Listener());
    }

    @Override
    public synchronized void subscribeBinance(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return;
        sendOne("subscribe", binanceEntry(symbols));
    }

    @Override
    public synchronized void unsubscribeBinance(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return;
        sendOne("unsubscribe", binanceEntry(symbols));
    }

    @Override
    public synchronized void subscribeChainlink(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return;
        send("subscribe", chainlinkEntries(symbols));
    }

    @Override
    public synchronized void unsubscribeChainlink(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return;
        send("unsubscribe", chainlinkEntries(symbols));
    }

    @Override
    public synchronized void subscribeComments(List<CommentSubscription> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) return;
        send("subscribe", commentEntries(subscriptions));
    }

    @Override
    public synchronized void unsubscribeComments(List<CommentSubscription> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) return;
        send("unsubscribe", commentEntries(subscriptions));
    }

    @Override
    public synchronized void close() {
        closed = true;
        cancelHeartbeat();
        if (socket != null) {
            socket.close(1000, "Client closed");
            socket = null;
        }
    }

    // ------------------------------------------------------------------ //
    // Wire frame construction                                             //
    // ------------------------------------------------------------------ //

    private ObjectNode binanceEntry(List<String> symbols) {
        ObjectNode entry = mapper.createObjectNode();
        entry.put("topic", TOPIC_BINANCE);
        entry.put("type", "update");
        entry.put("filters", String.join(",", symbols)); // documented format: comma-separated symbols
        return entry;
    }

    /** One entry per symbol: the documented Chainlink filter carries a single symbol each. */
    private List<ObjectNode> chainlinkEntries(List<String> symbols) {
        List<ObjectNode> entries = new java.util.ArrayList<>();
        for (String symbol : symbols) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("topic", TOPIC_CHAINLINK);
            entry.put("type", "*");
            ObjectNode filter = mapper.createObjectNode().put("symbol", symbol);
            entry.put("filters", filter.toString()); // escaped JSON string, per docs
            entries.add(entry);
        }
        return entries;
    }

    private List<ObjectNode> commentEntries(List<CommentSubscription> subscriptions) {
        List<ObjectNode> entries = new java.util.ArrayList<>();
        for (CommentSubscription s : subscriptions) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("topic", TOPIC_COMMENTS);
            entry.put("type", s.type().wireValue());
            if (s.entityType().isPresent()) {
                ObjectNode filter = mapper.createObjectNode();
                filter.put("parentEntityID", s.entityId().orElseThrow());
                filter.put("parentEntityType", s.entityType().orElseThrow().wireValue());
                entry.put("filters", filter.toString());
            }
            entries.add(entry);
        }
        return entries;
    }

    private void sendOne(String action, ObjectNode entry) {
        send(action, List.of(entry));
    }

    private void send(String action, List<ObjectNode> entries) {
        WebSocket ws = socket;
        if (ws == null || entries.isEmpty()) return;
        try {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("action", action);
            ArrayNode array = msg.putArray("subscriptions");
            entries.forEach(array::add);
            ws.send(mapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RTDS {} request", action, e);
        }
    }

    /** The full authoritative state, sent as one frame right after each (re)connect. */
    private void sendInitialState(WebSocket ws) {
        RtdsSubscriptions state = stateSupplier.get();
        List<ObjectNode> entries = new java.util.ArrayList<>();
        if (!state.binanceSymbols().isEmpty()) {
            entries.add(binanceEntry(state.binanceSymbols()));
        }
        entries.addAll(chainlinkEntries(state.chainlinkSymbols()));
        entries.addAll(commentEntries(state.comments()));
        if (entries.isEmpty()) return;
        try {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("action", "subscribe");
            ArrayNode array = msg.putArray("subscriptions");
            entries.forEach(array::add);
            ws.send(mapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RTDS initial subscribe request", e);
        }
    }

    // ------------------------------------------------------------------ //
    // Heartbeat                                                            //
    // ------------------------------------------------------------------ //

    private void startHeartbeat() {
        cancelHeartbeat();
        if (closed || pingIntervalMs <= 0) {
            return;
        }
        try {
            heartbeat = scheduler.scheduleAtFixedRate(
                    this::sendPing, pingIntervalMs, pingIntervalMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // close() won the race
        }
    }

    private void sendPing() {
        WebSocket ws = socket;
        if (ws == null || closed) return;
        try {
            ws.send(PING_FRAME);
        } catch (RuntimeException e) {
            log.debug("RTDS heartbeat send failed: {}", e.toString());
        }
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeat;
        if (task != null) {
            task.cancel(false);
        }
        heartbeat = null;
    }

    // ------------------------------------------------------------------ //
    // Reconnect                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Exponential backoff, capped at {@code maxReconnectDelayMs}; the attempt counter resets only
     * after {@code stableConnectionMs} uptime, so a handshake-then-close loop burns its budget.
     */
    private void scheduleReconnect() {
        if (closed) return;
        long uptime = openedAtMs.get() == 0 ? 0 : System.currentTimeMillis() - openedAtMs.get();
        if (uptime >= stableConnectionMs) {
            attempt.set(0);
        }
        openedAtMs.set(0);

        int n = attempt.incrementAndGet();
        if (maxReconnectAttempts > 0 && n > maxReconnectAttempts) {
            log.warn("RTDS: max reconnect attempts ({}) reached — giving up", maxReconnectAttempts);
            return;
        }
        long delay = Math.min(reconnectDelayMs * (1L << Math.min(n - 1, 30)), maxReconnectDelayMs);
        try {
            scheduler.schedule(this::doReconnect, delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // close() won the race after the closed check above
        }
    }

    private synchronized void doReconnect() {
        if (closed || socket != null) return;
        open();
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            log.warn("RTDS sink threw from {}: {}", what, e.toString(), e);
        }
    }

    // ------------------------------------------------------------------ //
    // OkHttp listener                                                      //
    // ------------------------------------------------------------------ //

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            long gen = generation.incrementAndGet();
            openedAtMs.set(System.currentTimeMillis());
            // Signalled BEFORE the subscription frame: everything after belongs to this generation.
            safely("onResubscribe", () -> sink.onResubscribe(gen));
            sendInitialState(ws);
            startHeartbeat();
            safely("onOpen", () -> sink.onOpen(gen));
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            eventMapper.dispatch(text, sink);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            if (closed) return;
            long gen = generation.get();
            try {
                log.error("RTDS channel failure", t);
                cancelHeartbeat();
                synchronized (RtdsChannelConnection.this) {
                    socket = null;
                }
                Exception error = t instanceof Exception ex ? ex : new RuntimeException(t);
                safely("onError", () -> sink.onError(gen, error));
            } finally {
                scheduleReconnect();
            }
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            ws.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            if (closed) return;
            long gen = generation.get();
            try {
                cancelHeartbeat();
                synchronized (RtdsChannelConnection.this) {
                    socket = null;
                }
                safely("onClose", () -> sink.onClose(gen, code, reason));
            } finally {
                scheduleReconnect();
            }
        }
    }
}
