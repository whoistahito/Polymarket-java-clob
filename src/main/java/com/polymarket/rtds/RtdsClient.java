package com.polymarket.rtds;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.ConnectionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real-Time Data Socket (RTDS) client for {@code wss://ws-live-data.polymarket.com}.
 *
 * <p>Streams crypto prices (Binance), Chainlink oracle prices, and live market
 * comments. Separate from {@link com.polymarket.ws.WsClient} (CLOB streams).
 * Incoming frames are dispatched to a {@link RtdsListener} as {@link RtdsMessage}s.
 *
 * <p>Mirrors the Rust SDK {@code rtds::Client} (concepts, not code): topic-based
 * subscriptions, the crypto/chainlink filter serialization quirk, optional CLOB
 * auth for comments, and auto-reconnect with re-subscription.
 *
 * <pre>{@code
 * RtdsClient rtds = RtdsClient.builder().listener(myListener).build();
 * rtds.subscribeCryptoPrices(List.of("btcusdt", "ethusdt"));
 * }</pre>
 *
 * <p>Re-subscribes to all active subscriptions on reconnect; no topic refcounting.
 */
public final class RtdsClient {

    /** Default RTDS WebSocket URL. */
    public static final String DEFAULT_RTDS_URL = "wss://ws-live-data.polymarket.com";

    private static final Logger log = LoggerFactory.getLogger(RtdsClient.class);

    private final String url;
    private final OkHttpClient okHttp;
    private final RtdsListener listener;
    private final ApiKeyCreds apiKeyCreds;

    private final int maxReconnectAttempts; // <=0 means unlimited
    private final long reconnectDelayMs;
    private final long maxReconnectDelayMs;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-rtds-reconnect");
            t.setDaemon(true);
            return t;
        });

    private volatile WebSocket ws;
    private volatile boolean closed = false;

    private final AtomicReference<ConnectionState> state =
        new AtomicReference<>(ConnectionState.disconnected());
    private final AtomicInteger attempt = new AtomicInteger(0);

    /** Active subscriptions, replayed on reconnect. */
    private final List<Subscription> active = new CopyOnWriteArrayList<>();

    private RtdsClient(Builder b) {
        this.url = b.url;
        this.okHttp = b.okHttp != null ? b.okHttp : defaultOkHttpClient();
        this.listener = Objects.requireNonNull(b.listener, "listener must not be null");
        this.apiKeyCreds = b.apiKeyCreds;
        this.maxReconnectAttempts = b.maxReconnectAttempts;
        this.reconnectDelayMs = b.reconnectDelayMs;
        this.maxReconnectDelayMs = b.maxReconnectDelayMs;
    }

    public static Builder builder() { return new Builder(); }

    // ------------------------------------------------------------------ //
    // Subscribe                                                            //
    // ------------------------------------------------------------------ //

    /** Subscribe to Binance crypto prices; {@code null}/empty {@code symbols} = all pairs. */
    public void subscribeCryptoPrices(List<String> symbols) {
        subscribe(Subscription.cryptoPrices(symbols));
    }

    /** Subscribe to Chainlink prices; {@code null} {@code symbol} = all symbols. */
    public void subscribeChainlinkPrices(String symbol) {
        subscribe(Subscription.chainlinkPrices(symbol));
    }

    /** Subscribe to comment events (unauthenticated); {@code null} {@code type} = all. */
    public void subscribeComments(CommentType type) {
        subscribe(Subscription.comments(type));
    }

    /**
     * Subscribe to comment events with CLOB auth. Requires {@code apiKeyCreds} on the builder.
     */
    public void subscribeCommentsAuthenticated(CommentType type) {
        if (apiKeyCreds == null) {
            throw new IllegalStateException("apiKeyCreds required for authenticated comments");
        }
        subscribe(Subscription.comments(type).withClobAuth(apiKeyCreds));
    }

    /** Subscribe with a hand-built {@link Subscription} (escape hatch for custom topics). */
    public synchronized void subscribe(Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription");
        active.add(subscription);
        if (ws == null) {
            state.set(ConnectionState.connecting());
            attempt.set(0);
            ws = openChannel();
        }
        send("subscribe", List.of(subscription));
    }

    // ------------------------------------------------------------------ //
    // Unsubscribe                                                          //
    // ------------------------------------------------------------------ //

    /** Unsubscribe from Binance crypto prices. */
    public void unsubscribeCryptoPrices() {
        unsubscribe(Subscription.TOPIC_CRYPTO);
    }

    /** Unsubscribe from Chainlink prices. */
    public void unsubscribeChainlinkPrices() {
        unsubscribe(Subscription.TOPIC_CHAINLINK);
    }

    /** Unsubscribe from comment events. */
    public void unsubscribeComments() {
        unsubscribe(Subscription.TOPIC_COMMENTS);
    }

    private synchronized void unsubscribe(String topic) {
        if (ws == null) return;
        List<Subscription> removed = new ArrayList<>();
        active.removeIf(s -> {
            if (s.topic().equals(topic)) { removed.add(s); return true; }
            return false;
        });
        if (!removed.isEmpty()) {
            send("unsubscribe", removed);
        }
    }

    // ------------------------------------------------------------------ //
    // Lifecycle / health                                                   //
    // ------------------------------------------------------------------ //

    /** Close the connection and stop reconnection. */
    public synchronized void close() {
        closed = true;
        scheduler.shutdownNow();
        if (ws != null) {
            ws.close(1000, "Client closed");
            ws = null;
        }
        state.set(ConnectionState.disconnected());
    }

    public ConnectionState getConnectionState() { return state.get(); }

    public boolean isConnected() { return state.get().isConnected(); }

    /** Number of active subscriptions. */
    public int getSubscriptionCount() { return active.size(); }

    // ------------------------------------------------------------------ //
    // Internals                                                            //
    // ------------------------------------------------------------------ //

    private WebSocket openChannel() {
        Request request = new Request.Builder().url(url).build();
        log.debug("Connecting to RTDS: {}", url);
        return okHttp.newWebSocket(request, new Listener());
    }

    private void send(String action, List<Subscription> subs) {
        WebSocket current = ws;
        if (current == null) return;
        current.send(Subscription.requestJson(action, subs));
    }

    private void dispatch(String text) {
        try {
            for (RtdsMessage msg : RtdsMessage.parse(text)) {
                listener.onMessage(msg);
            }
        } catch (Exception e) {
            log.warn("Failed to parse RTDS frame: {}", text, e);
            listener.onError(e);
        }
    }

    private static OkHttpClient defaultOkHttpClient() {
        return new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();
    }

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket socket, Response response) {
            log.debug("RTDS channel opened");
            attempt.set(0);
            state.set(ConnectionState.connected());
            listener.onOpen();
        }

        @Override
        public void onMessage(WebSocket socket, String text) {
            dispatch(text);
        }

        @Override
        public void onFailure(WebSocket socket, Throwable t, Response r) {
            log.error("RTDS channel failure", t);
            state.set(ConnectionState.disconnected());
            synchronized (RtdsClient.this) { ws = null; }
            listener.onError(t instanceof Exception ex ? ex : new RuntimeException(t));
            scheduleReconnect();
        }

        @Override
        public void onClosed(WebSocket socket, int code, String reason) {
            log.debug("RTDS channel closed: {} {}", code, reason);
            state.set(ConnectionState.disconnected());
            synchronized (RtdsClient.this) { ws = null; }
            listener.onClose(code, reason);
        }
    }

    private void scheduleReconnect() {
        if (closed) return;
        int n = attempt.incrementAndGet();
        if (maxReconnectAttempts > 0 && n > maxReconnectAttempts) {
            log.warn("RTDS: max reconnect attempts ({}) reached — giving up", maxReconnectAttempts);
            return;
        }
        long delay = Math.min(reconnectDelayMs * (1L << Math.min(n - 1, 30)), maxReconnectDelayMs);
        state.set(ConnectionState.reconnecting(n));
        log.info("RTDS: scheduling reconnect attempt {} in {} ms", n, delay);
        scheduler.schedule(this::doReconnect, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void doReconnect() {
        if (closed || ws != null || active.isEmpty()) return;
        state.set(ConnectionState.connecting());
        ws = openChannel();
        send("subscribe", new ArrayList<>(active));
    }

    // ------------------------------------------------------------------ //
    // Builder                                                              //
    // ------------------------------------------------------------------ //

    public static final class Builder {
        private String url = DEFAULT_RTDS_URL;
        private OkHttpClient okHttp;
        private RtdsListener listener;
        private ApiKeyCreds apiKeyCreds;
        private int maxReconnectAttempts = 0;       // unlimited
        private long reconnectDelayMs = 1_000L;
        private long maxReconnectDelayMs = 60_000L;

        private Builder() {}

        public Builder url(String url) {
            this.url = Objects.requireNonNull(url);
            return this;
        }

        public Builder okHttpClient(OkHttpClient okHttp) {
            this.okHttp = okHttp;
            return this;
        }

        public Builder listener(RtdsListener listener) {
            this.listener = Objects.requireNonNull(listener);
            return this;
        }

        /** CLOB credentials for {@link RtdsClient#subscribeCommentsAuthenticated(CommentType)}. */
        public Builder apiKeyCreds(ApiKeyCreds creds) {
            this.apiKeyCreds = creds;
            return this;
        }

        public Builder maxReconnectAttempts(int max) {
            this.maxReconnectAttempts = max;
            return this;
        }

        public Builder reconnectDelayMs(long delayMs) {
            if (delayMs <= 0) throw new IllegalArgumentException("reconnectDelayMs must be > 0");
            this.reconnectDelayMs = delayMs;
            return this;
        }

        public Builder maxReconnectDelayMs(long maxDelayMs) {
            if (maxDelayMs <= 0) throw new IllegalArgumentException("maxReconnectDelayMs must be > 0");
            this.maxReconnectDelayMs = maxDelayMs;
            return this;
        }

        public RtdsClient build() {
            return new RtdsClient(this);
        }
    }
}
