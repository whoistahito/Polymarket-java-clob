package com.polymarket.internal.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.streaming.StreamChannel;
import com.polymarket.streaming.StreamConnection;
import com.polymarket.streaming.StreamEventSink;
import java.util.Collection;
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
 * One logical channel, reconnecting transparently across however many physical sockets it takes.
 * Ported from the 1.0 {@code WsClient}'s per-channel reconnect/backoff/heartbeat algorithm.
 */
final class ChannelConnection implements StreamConnection {

    private static final Logger log = LoggerFactory.getLogger(ChannelConnection.class);
    private static final String PING_FRAME = "PING";

    private final OkHttpClient okHttp;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper;
    private final StreamEventMapper eventMapper;
    private final String url;
    private final StreamChannel channelType;
    private final ApiCredentials credentials; // non-null only for the user channel
    private final Supplier<List<String>> idsSupplier;
    private final StreamEventSink sink;

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

    ChannelConnection(OkHttpClient okHttp, ScheduledExecutorService scheduler, ObjectMapper mapper,
            String url, StreamChannel channelType, ApiCredentials credentials,
            Supplier<List<String>> idsSupplier, StreamEventSink sink, long pingIntervalMs,
            long reconnectDelayMs, long maxReconnectDelayMs, long stableConnectionMs,
            int maxReconnectAttempts) {
        this.okHttp = okHttp;
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.eventMapper = new StreamEventMapper(mapper);
        this.url = url;
        this.channelType = channelType;
        this.credentials = credentials;
        this.idsSupplier = idsSupplier;
        this.sink = sink;
        this.pingIntervalMs = pingIntervalMs;
        this.reconnectDelayMs = reconnectDelayMs;
        this.maxReconnectDelayMs = maxReconnectDelayMs;
        this.stableConnectionMs = stableConnectionMs;
        this.maxReconnectAttempts = maxReconnectAttempts;
        open();
    }

    private boolean isUser() {
        return channelType == StreamChannel.USER;
    }

    private void open() {
        Request request = new Request.Builder().url(url).build();
        socket = okHttp.newWebSocket(request, new Listener());
    }

    @Override
    public synchronized void subscribe(List<String> ids) {
        WebSocket ws = socket;
        if (ws != null && ids != null && !ids.isEmpty()) {
            send(ws, ids, "subscribe", false);
        }
    }

    @Override
    public synchronized void unsubscribe(List<String> ids) {
        WebSocket ws = socket;
        if (ws != null && ids != null && !ids.isEmpty()) {
            send(ws, ids, "unsubscribe", false);
        }
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

    private void send(WebSocket ws, Collection<String> ids, String operation, boolean initial) {
        try {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("type", isUser() ? "user" : "market");
            msg.put("operation", operation);
            ArrayNode array = msg.putArray(isUser() ? "markets" : "assets_ids");
            ids.forEach(array::add);
            if (initial) {
                msg.put("initial_dump", true);
            }
            // Credentials ride only the initial (post-handshake) frame; a dynamic update on an
            // already-authenticated socket must not repeat secrets over the wire.
            if (isUser() && initial) {
                appendAuth(msg);
            }
            ws.send(mapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {} {} request", channelType, operation, e);
        }
    }

    /**
     * The official rs-clob-client-v2 shape: a nested {@code auth} object with {@code apiKey},
     * {@code secret}, {@code passphrase} — no signature, timestamp, or wallet address.
     */
    private void appendAuth(ObjectNode node) {
        ObjectNode auth = node.putObject("auth");
        auth.put("apiKey", credentials.key());
        auth.put("secret", credentials.secret());
        auth.put("passphrase", credentials.passphrase());
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
        if (ws == null || closed) {
            return;
        }
        try {
            ws.send(PING_FRAME);
        } catch (RuntimeException e) {
            log.debug("{} channel heartbeat send failed: {}", channelType, e.toString());
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
        if (closed) {
            return;
        }
        // User always reconnects (credentials imply intent); market only while its set is non-empty.
        if (!isUser() && idsSupplier.get().isEmpty()) {
            log.debug("{} channel: nothing subscribed — not reconnecting", channelType);
            return;
        }
        long uptime = openedAtMs.get() == 0 ? 0 : System.currentTimeMillis() - openedAtMs.get();
        if (uptime >= stableConnectionMs) {
            attempt.set(0);
        }
        openedAtMs.set(0);

        int n = attempt.incrementAndGet();
        if (maxReconnectAttempts > 0 && n > maxReconnectAttempts) {
            log.warn("{} channel: max reconnect attempts ({}) reached — giving up",
                    channelType, maxReconnectAttempts);
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
        if (closed || socket != null) {
            return;
        }
        if (!isUser() && idsSupplier.get().isEmpty()) {
            return;
        }
        open();
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            log.warn("Stream sink threw from {}: {}", what, e.toString(), e);
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
            List<String> ids = idsSupplier.get();
            // Signalled BEFORE the subscription frame: everything after belongs to this generation.
            safely("onResubscribe", () -> sink.onResubscribe(channelType, gen));
            if (isUser() || !ids.isEmpty()) {
                send(ws, ids, "subscribe", true);
            }
            startHeartbeat();
            safely("onOpen", () -> sink.onOpen(channelType, gen));
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            eventMapper.dispatch(text, sink);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            if (closed) {
                return;
            }
            long gen = generation.get();
            try {
                log.error("{} channel failure", channelType, t);
                cancelHeartbeat();
                synchronized (ChannelConnection.this) {
                    socket = null;
                }
                Exception error = t instanceof Exception ex ? ex : new RuntimeException(t);
                safely("onError", () -> sink.onError(channelType, gen, error));
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
            if (closed) {
                return;
            }
            long gen = generation.get();
            try {
                cancelHeartbeat();
                synchronized (ChannelConnection.this) {
                    socket = null;
                }
                safely("onClose", () -> sink.onClose(channelType, gen, code, reason));
            } finally {
                scheduleReconnect();
            }
        }
    }
}
