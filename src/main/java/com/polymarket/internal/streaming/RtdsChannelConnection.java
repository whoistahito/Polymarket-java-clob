package com.polymarket.internal.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymarket.streaming.CommentSubscription;
import com.polymarket.streaming.RtdsConnection;
import com.polymarket.streaming.RtdsEventSink;
import com.polymarket.streaming.RtdsSubscriptions;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one live RTDS socket, reconnecting transparently. Its wire envelope and topics are RTDS's
 * own, and its reconnect hardening intentionally remains distinct from CLOB {@code ChannelConnection}.
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

    private final RtdsEventSink sink;

    private final long pingIntervalMs;
    private final long reconnectDelayMs;
    private final long maxReconnectDelayMs;
    private final long stableConnectionMs;
    private final int maxReconnectAttempts;

    /**
     * The subjects, the socket and the "initial frame already sent" flag move together under this
     * monitor: that single rule is what stops an update overtaking or duplicating the initial frame.
     */
    private RtdsSubscriptions subjects;
    private WebSocket socket;
    private boolean initialSent;
    private boolean socketOpened;
    /** True while a reconnect is already queued, so a subscribe does not undercut its backoff. */
    private boolean reconnectScheduled;

    private volatile boolean closed;
    private final AtomicLong generation = new AtomicLong(0);
    private final AtomicInteger attempt = new AtomicInteger(0);
    private final AtomicLong openedAtMs = new AtomicLong(0);
    private volatile ScheduledFuture<?> heartbeat;

    RtdsChannelConnection(OkHttpClient okHttp, ScheduledExecutorService scheduler, ObjectMapper mapper, String url,
            RtdsSubscriptions subjects, RtdsEventSink sink, long pingIntervalMs,
            long reconnectDelayMs, long maxReconnectDelayMs, long stableConnectionMs, int maxReconnectAttempts) {
        this.okHttp = okHttp;
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.eventMapper = new RtdsEventMapper(mapper);
        this.url = url;
        this.subjects = subjects;
        this.sink = sink;
        this.pingIntervalMs = pingIntervalMs;
        this.reconnectDelayMs = reconnectDelayMs;
        this.maxReconnectDelayMs = maxReconnectDelayMs;
        this.stableConnectionMs = stableConnectionMs;
        this.maxReconnectAttempts = maxReconnectAttempts;
        open();
    }

    private synchronized void open() {
        Request request = new Request.Builder().url(url).build();
        initialSent = false;
        socketOpened = false;
        socket = okHttp.newWebSocket(request, new Listener());
    }

    @Override
    public synchronized void subscription(RtdsSubscriptions current) {
        RtdsSubscriptions previous = subjects;
        subjects = current;
        if (socket == null) {
            // Nothing will reopen this socket once the attempt budget is spent, so a later
            // subscribe has to. A queued reconnect keeps its backoff and carries the set set above.
            if (!closed && !reconnectScheduled) {
                open();
            }
            return;
        }
        if (!initialSent) {
            return; // the initial frame has not gone out yet - it will carry the whole set
        }

        List<ObjectNode> subscriptions = new java.util.ArrayList<>();
        List<ObjectNode> unsubscriptions = new java.util.ArrayList<>();
        if (symbolsChanged(previous.binanceSymbols(), current.binanceSymbols())) {
            if (current.binanceSymbols().isEmpty()) {
                if (!previous.binanceSymbols().isEmpty()) {
                    unsubscriptions.add(binanceEntry(previous.binanceSymbols()));
                }
            } else {
                subscriptions.add(binanceEntry(current.binanceSymbols()));
            }
        }
        if (symbolsChanged(previous.chainlinkSymbols(), current.chainlinkSymbols())) {
            if (current.chainlinkSymbols().isEmpty()) {
                if (!previous.chainlinkSymbols().isEmpty()) {
                    unsubscriptions.add(chainlinkEntry(previous.chainlinkSymbols()));
                }
            } else {
                subscriptions.add(chainlinkEntry(current.chainlinkSymbols()));
            }
        }

        // Comments retain their additive/removal delta semantics.
        subscriptions.addAll(commentEntries(delta(current, previous).comments()));
        unsubscriptions.addAll(commentEntries(delta(previous, current).comments()));
        send("subscribe", subscriptions);
        send("unsubscribe", unsubscriptions);
    }

    @Override
    public synchronized boolean refresh() {
        if (closed || socket == null || !socketOpened || reconnectScheduled) {
            return false;
        }
        WebSocket replaced = socket;
        socket = null;
        initialSent = false;
        socketOpened = false;
        reconnectScheduled = true;
        openedAtMs.set(0);

        cancelHeartbeat();
        cancelSocket(replaced);
        try {
            scheduler.schedule(this::doReconnect, reconnectDelayMs, TimeUnit.MILLISECONDS);
            log.warn("RTDS physical socket refresh requested; replacement scheduled (delay {} ms)",
                    reconnectDelayMs);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            markReconnectScheduled(false);
            return false;
        }
    }

    /** Everything in {@code left} that {@code right} does not already carry. */
    private static RtdsSubscriptions delta(RtdsSubscriptions left, RtdsSubscriptions right) {
        List<String> binance = new java.util.ArrayList<>(left.binanceSymbols());
        binance.removeAll(right.binanceSymbols());
        List<String> chainlink = new java.util.ArrayList<>(left.chainlinkSymbols());
        chainlink.removeAll(right.chainlinkSymbols());
        List<CommentSubscription> comments = new java.util.ArrayList<>(left.comments());
        comments.removeAll(right.comments());
        return new RtdsSubscriptions(binance, chainlink, comments);
    }

    private static boolean symbolsChanged(List<String> previous, List<String> current) {
        return !new java.util.HashSet<>(previous).equals(new java.util.HashSet<>(current));
    }

    private List<ObjectNode> entriesFor(RtdsSubscriptions state) {
        List<ObjectNode> entries = new java.util.ArrayList<>();
        if (!state.binanceSymbols().isEmpty()) {
            entries.add(binanceEntry(state.binanceSymbols()));
        }
        if (!state.chainlinkSymbols().isEmpty()) {
            entries.add(chainlinkEntry(state.chainlinkSymbols()));
        }
        entries.addAll(commentEntries(state.comments()));
        return entries;
    }

    @Override
    public synchronized void close() {
        closed = true;
        cancelHeartbeat();
        initialSent = false;
        socketOpened = false;
        if (socket != null) {
            socket.close(1000, "Client closed");
            socket = null;
        }
    }

    // ------------------------------------------------------------------ //
    // Wire frame construction                                             //
    // ------------------------------------------------------------------ //

    private ObjectNode binanceEntry(List<String> symbols) {
        return priceEntry(TOPIC_BINANCE, "update", symbols);
    }

    private ObjectNode chainlinkEntry(List<String> symbols) {
        return priceEntry(TOPIC_CHAINLINK, "*", symbols);
    }

    /** A single-symbol price filter is a JSON string; multi-symbol topics are left unfiltered. */
    private ObjectNode priceEntry(String topic, String type, List<String> symbols) {
        List<String> distinct = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(symbols));
        ObjectNode entry = mapper.createObjectNode();
        entry.put("topic", topic);
        entry.put("type", type);
        if (distinct.size() == 1) {
            ObjectNode filter = mapper.createObjectNode().put("symbol", distinct.get(0));
            entry.put("filters", filter.toString()); // escaped JSON string, per docs
        }
        return entry;
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

    private void send(String action, List<ObjectNode> entries) {
        WebSocket ws = socket;
        if (ws == null || !initialSent || entries.isEmpty()) return;
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
    private InitialSendFailure sendInitialState(WebSocket ws) {
        List<ObjectNode> entries = entriesFor(subjects);
        if (entries.isEmpty()) return null;
        try {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("action", "subscribe");
            ArrayNode array = msg.putArray("subscriptions");
            entries.forEach(array::add);
            if (ws.send(mapper.writeValueAsString(msg))) {
                return null;
            }
            return new InitialSendFailure(
                    new IOException("RTDS initial subscription send was rejected"), true);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RTDS initial subscribe request", e);
            return new InitialSendFailure(e, false);
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
    private ReconnectResult scheduleReconnect() {
        long delay;
        int n;
        synchronized (this) {
            if (closed || socket != null || reconnectScheduled) {
                return ReconnectResult.suppressed();
            }
            long uptime = openedAtMs.get() == 0 ? 0 : System.currentTimeMillis() - openedAtMs.get();
            if (uptime >= stableConnectionMs) {
                attempt.set(0);
            }
            openedAtMs.set(0);

            n = attempt.incrementAndGet();
            if (maxReconnectAttempts > 0 && n > maxReconnectAttempts) {
                return ReconnectResult.exhausted(n);
            }
            delay = Math.min(reconnectDelayMs * (1L << Math.min(n - 1, 30)), maxReconnectDelayMs);
            reconnectScheduled = true;
        }
        try {
            scheduler.schedule(this::doReconnect, delay, TimeUnit.MILLISECONDS);
            return ReconnectResult.scheduled(n, delay);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            markReconnectScheduled(false); // close() won the race after the closed check above
            return ReconnectResult.rejected();
        }
    }

    private synchronized void markReconnectScheduled(boolean scheduled) {
        this.reconnectScheduled = scheduled;
    }

    private synchronized Disconnected disconnect(WebSocket expected) {
        if (closed || socket != expected) {
            return null;
        }
        Disconnected disconnected = new Disconnected(generation.get(), socketOpened);
        socket = null;
        initialSent = false;
        socketOpened = false;
        return disconnected;
    }

    private synchronized boolean isCurrent(WebSocket expected) {
        return !closed && socket == expected;
    }

    private synchronized void cancelIfSuperseded(WebSocket expected) {
        if (!closed && socket != expected) {
            cancelSocket(expected);
        }
    }

    private void cancelSocket(WebSocket ws) {
        try {
            ws.cancel();
        } catch (RuntimeException e) {
            log.debug("RTDS socket cancellation failed: {}", e.toString());
        }
    }

    private void fail(WebSocket ws, Throwable failure, Disconnected disconnected, boolean cancel,
            boolean initialSendRejected) {
        cancelHeartbeat();
        if (cancel) {
            cancelSocket(ws);
        }

        ReconnectResult outcome;
        try {
            Exception error = asException(failure);
            safely("onError", () -> sink.onError(disconnected.generation(), error));
        } finally {
            outcome = scheduleReconnect();
        }
        logFailure(failure, disconnected, outcome, initialSendRejected);
    }

    private void logFailure(Throwable failure, Disconnected disconnected, ReconnectResult outcome,
            boolean initialSendRejected) {
        boolean connectPhase = !disconnected.previouslyOpen();
        boolean livenessTimeout =
                disconnected.previouslyOpen() && failure instanceof SocketTimeoutException;
        if (outcome.status() == ReconnectStatus.SCHEDULED
                && (initialSendRejected || livenessTimeout
                        || (disconnected.previouslyOpen() && isRecoverableTransportLoss(failure))
                        || (connectPhase && isRecoverableConnectLoss(failure)))) {
            String kind = initialSendRejected ? "initial subscription send rejected"
                    : livenessTimeout ? "liveness ping timeout"
                    : connectPhase ? "connect failure" : "transport loss after open";
            log.warn("RTDS {} — reconnect scheduled (attempt {}, delay {} ms): {}", kind,
                    outcome.attempt(), outcome.delayMs(), concise(failure));
            return;
        }
        String suffix = switch (outcome.status()) {
            case EXHAUSTED -> "; reconnect attempts exhausted (attempt " + outcome.attempt()
                    + ", limit " + maxReconnectAttempts + ")";
            case SCHEDULED -> "; reconnect scheduled (attempt " + outcome.attempt()
                    + ", delay " + outcome.delayMs() + " ms)";
            case SUPPRESSED -> "; reconnect suppressed";
            case REJECTED -> "; reconnect scheduling rejected";
        };
        log.error("RTDS channel failure{}", suffix, failure);
    }

    private static Exception asException(Throwable failure) {
        return failure instanceof Exception exception ? exception : new RuntimeException(failure);
    }

    private static boolean isRecoverableTransportLoss(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof EOFException || current instanceof SSLException) {
                return true;
            }
            if (current instanceof SocketException socketException) {
                String message = socketException.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase(java.util.Locale.ROOT);
                    if (lower.contains("reset") || lower.contains("broken pipe")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isRecoverableConnectLoss(Throwable failure) {
        boolean recoverable = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SSLException) {
                return false;
            }
            if (current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException) {
                recoverable = true;
            }
        }
        return recoverable;
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }

    private synchronized void doReconnect() {
        reconnectScheduled = false;
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
            long gen;
            synchronized (RtdsChannelConnection.this) {
                if (closed) {
                    return;
                }
                if (ws != socket) {
                    cancelSocket(ws);
                    return;
                }
                gen = generation.incrementAndGet();
                openedAtMs.set(System.currentTimeMillis());
                socketOpened = true;
            }
            // Signalled BEFORE the subscription frame: everything after belongs to this generation.
            safely("onResubscribe", () -> sink.onResubscribe(gen));
            InitialSendFailure initialFailure;
            Disconnected disconnected = null;
            synchronized (RtdsChannelConnection.this) {
                if (closed) {
                    return;
                }
                if (ws != socket) {
                    cancelSocket(ws);
                    return;
                }
                initialFailure = sendInitialState(ws);
                if (initialFailure == null) {
                    initialSent = true;
                } else {
                    // A rejected initial frame is a failed connection, not an open one. Detach
                    // before canceling so OkHttp's later terminal callback cannot reconnect twice.
                    disconnected = disconnect(ws);
                }
            }
            if (initialFailure != null) {
                if (disconnected != null) {
                    fail(ws, initialFailure.error(), disconnected, true, initialFailure.sendRejected());
                }
                return;
            }
            startHeartbeat();
            safely("onOpen", () -> sink.onOpen(gen));
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            if (!isCurrent(ws)) {
                cancelIfSuperseded(ws);
                return; // a frame in flight when close() landed reaches no application callback
            }
            eventMapper.dispatch(text, sink);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            Disconnected disconnected = disconnect(ws);
            if (disconnected == null) {
                return;
            }
            fail(ws, t, disconnected, false, false);
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            if (!isCurrent(ws)) {
                cancelIfSuperseded(ws);
                return;
            }
            ws.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            Disconnected disconnected = disconnect(ws);
            if (disconnected == null) {
                return;
            }
            ReconnectResult outcome;
            try {
                cancelHeartbeat();
                safely("onClose", () -> sink.onClose(disconnected.generation(), code, reason));
            } finally {
                outcome = scheduleReconnect();
            }
            logClose(code, reason, outcome);
        }
    }

    private void logClose(int code, String reason, ReconnectResult outcome) {
        switch (outcome.status()) {
            case SCHEDULED -> log.info(
                    "RTDS channel closed (code {}, reason '{}'); reconnect scheduled (attempt {}, delay {} ms)",
                    code, reason, outcome.attempt(), outcome.delayMs());
            case EXHAUSTED -> log.error(
                    "RTDS channel closed (code {}, reason '{}'); reconnect attempts exhausted (attempt {}, limit {})",
                    code, reason, outcome.attempt(), maxReconnectAttempts);
            case SUPPRESSED -> log.info(
                    "RTDS channel closed (code {}, reason '{}'); reconnect suppressed", code, reason);
            case REJECTED -> log.error(
                    "RTDS channel closed (code {}, reason '{}'); reconnect scheduling rejected", code, reason);
        }
    }

    private record InitialSendFailure(Exception error, boolean sendRejected) {}

    private record Disconnected(long generation, boolean previouslyOpen) {}

    private record ReconnectResult(ReconnectStatus status, int attempt, long delayMs) {
        private static ReconnectResult scheduled(int attempt, long delayMs) {
            return new ReconnectResult(ReconnectStatus.SCHEDULED, attempt, delayMs);
        }

        private static ReconnectResult exhausted(int attempt) {
            return new ReconnectResult(ReconnectStatus.EXHAUSTED, attempt, 0);
        }

        private static ReconnectResult suppressed() {
            return new ReconnectResult(ReconnectStatus.SUPPRESSED, 0, 0);
        }

        private static ReconnectResult rejected() {
            return new ReconnectResult(ReconnectStatus.REJECTED, 0, 0);
        }
    }

    private enum ReconnectStatus {
        SCHEDULED,
        SUPPRESSED,
        REJECTED,
        EXHAUSTED
    }
}
