package com.polymarket.internal.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.streaming.StreamChannel;
import com.polymarket.streaming.StreamConnection;
import com.polymarket.streaming.StreamEventSink;
import com.polymarket.streaming.StreamTransport;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * OkHttp/Jackson adapter for {@link StreamTransport}: one shared client and scheduler for
 * reconnects and heartbeats across both channels, as the 1.0 {@code WsClient} did.
 */
public final class StreamingGateway implements StreamTransport, AutoCloseable {

    public static final String DEFAULT_WS_BASE = "wss://ws-subscriptions-clob.polymarket.com";
    private static final String MARKET_PATH = "/ws/market";
    private static final String USER_PATH = "/ws/user";

    private final String wsBase;
    private final OkHttpClient okHttp;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long pingIntervalMs;
    private final long reconnectDelayMs;
    private final long maxReconnectDelayMs;
    private final long stableConnectionMs;
    private final int maxReconnectAttempts;

    public StreamingGateway() {
        this(new Builder());
    }

    private StreamingGateway(Builder b) {
        this.wsBase = b.wsBase;
        this.okHttp = new OkHttpClient.Builder()
                .pingInterval(0, TimeUnit.SECONDS) // the documented heartbeat is a text PING, not a WS ping
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "polymarket-streaming-scheduler");
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
    public StreamConnection connectMarket(
            List<String> assetIds, boolean customEventsEnabled, StreamEventSink sink) {
        return new ChannelConnection(okHttp, scheduler, mapper, wsBase + MARKET_PATH,
                StreamChannel.MARKET, null, customEventsEnabled, assetIds, sink, pingIntervalMs,
                reconnectDelayMs, maxReconnectDelayMs, stableConnectionMs, maxReconnectAttempts);
    }

    @Override
    public StreamConnection connectUser(
            ApiCredentials credentials, List<String> markets, StreamEventSink sink) {
        return new ChannelConnection(okHttp, scheduler, mapper, wsBase + USER_PATH,
                StreamChannel.USER, credentials, false, markets, sink, pingIntervalMs, reconnectDelayMs,
                maxReconnectDelayMs, stableConnectionMs, maxReconnectAttempts);
    }

    /** Shuts down the shared scheduler and HTTP dispatcher. Individual connections close their sockets. */
    @Override
    public void close() {
        scheduler.shutdownNow();
        okHttp.dispatcher().executorService().shutdown();
        okHttp.connectionPool().evictAll();
    }

    public static final class Builder {
        private String wsBase = DEFAULT_WS_BASE;
        private long pingIntervalMs = 10_000L;
        private long reconnectDelayMs = 1_000L;
        private long maxReconnectDelayMs = 60_000L;
        private long stableConnectionMs = 30_000L;
        private int maxReconnectAttempts = 0;

        public Builder wsBase(String wsBase) {
            this.wsBase = wsBase;
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

        public StreamingGateway build() {
            return new StreamingGateway(this);
        }
    }
}
