package com.polymarket.internal.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.streaming.RtdsConnection;
import com.polymarket.streaming.RtdsEventSink;
import com.polymarket.streaming.RtdsSubscriptions;
import com.polymarket.streaming.RtdsTransport;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * OkHttp/Jackson adapter for {@link RtdsTransport}: connects to the Real-Time Data Service, a
 * separate protocol and host from the CLOB {@code StreamingGateway}.
 */
public final class RtdsGateway implements RtdsTransport, AutoCloseable {

    public static final String DEFAULT_RTDS_URL = "wss://ws-live-data.polymarket.com";

    private final String url;
    private final OkHttpClient okHttp;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long pingIntervalMs;
    private final long reconnectDelayMs;
    private final long maxReconnectDelayMs;
    private final long stableConnectionMs;
    private final int maxReconnectAttempts;

    public RtdsGateway() {
        this(new Builder());
    }

    private RtdsGateway(Builder b) {
        this.url = b.url;
        this.okHttp = new OkHttpClient.Builder()
                .pingInterval(0, TimeUnit.SECONDS) // the documented heartbeat is a text PING, not a WS ping
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "polymarket-rtds-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.pingIntervalMs = b.pingIntervalMs;
        this.reconnectDelayMs = b.reconnectDelayMs;
        this.maxReconnectDelayMs = b.maxReconnectDelayMs;
        this.stableConnectionMs = b.stableConnectionMs;
        this.maxReconnectAttempts = b.maxReconnectAttempts;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public RtdsConnection connect(RtdsSubscriptions subscriptions, RtdsEventSink sink) {
        return new RtdsChannelConnection(okHttp, scheduler, mapper, url, subscriptions, sink, pingIntervalMs,
                reconnectDelayMs, maxReconnectDelayMs, stableConnectionMs, maxReconnectAttempts);
    }

    /** True once every owned resource - scheduler, dispatcher and connection pool - is released. */
    public boolean isClosed() {
        return scheduler.isShutdown() && okHttp.dispatcher().executorService().isShutdown();
    }

    public int connectionPoolSize() {
        return okHttp.connectionPool().connectionCount();
    }

    /** Shuts down the shared scheduler and HTTP dispatcher. The connection closes its own socket. */
    @Override
    public void close() {
        scheduler.shutdownNow();
        // Cancel first: an open socket holds its connection, so evicting before it releases leaks one.
        okHttp.dispatcher().cancelAll();
        okHttp.dispatcher().executorService().shutdown();
        okHttp.connectionPool().evictAll();
    }

    public static final class Builder {
        private String url = DEFAULT_RTDS_URL;
        // The RTDS docs specify a 5-second text PING, distinct from the CLOB channels' 10-second one.
        private long pingIntervalMs = 5_000L;
        private long reconnectDelayMs = 1_000L;
        private long maxReconnectDelayMs = 60_000L;
        private long stableConnectionMs = 30_000L;
        private int maxReconnectAttempts = 0;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder pingIntervalMs(long ms) {
            this.pingIntervalMs = ms;
            return this;
        }

        public Builder reconnectDelayMs(long ms) {
            if (ms <= 0) throw new IllegalArgumentException("reconnectDelayMs must be > 0");
            this.reconnectDelayMs = ms;
            return this;
        }

        public Builder maxReconnectDelayMs(long ms) {
            if (ms <= 0) throw new IllegalArgumentException("maxReconnectDelayMs must be > 0");
            this.maxReconnectDelayMs = ms;
            return this;
        }

        public Builder stableConnectionMs(long ms) {
            if (ms < 0) throw new IllegalArgumentException("stableConnectionMs must be >= 0");
            this.stableConnectionMs = ms;
            return this;
        }

        public Builder maxReconnectAttempts(int max) {
            this.maxReconnectAttempts = max;
            return this;
        }

        public RtdsGateway build() {
            return new RtdsGateway(this);
        }
    }
}
