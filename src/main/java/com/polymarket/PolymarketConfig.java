package com.polymarket;

import java.net.URI;
import java.time.Duration;
import lombok.NonNull;

/**
 * SDK-owned network configuration. Exposes only JDK types, so a transport-library
 * upgrade cannot break a caller's source.
 */
public final class PolymarketConfig {

    private static final URI DEFAULT_CLOB = URI.create("https://clob.polymarket.com");
    private static final URI DEFAULT_GAMMA = URI.create("https://gamma-api.polymarket.com");
    private static final URI DEFAULT_DATA = URI.create("https://data-api.polymarket.com");
    private static final URI DEFAULT_GEOBLOCK = URI.create("https://polymarket.com");
    private static final URI DEFAULT_COMBO_MARKETS =
            URI.create("https://combos-rfq-api.polymarket.com");
    private static final URI DEFAULT_STREAM = URI.create("wss://ws-subscriptions-clob.polymarket.com");
    private static final URI DEFAULT_RTDS = URI.create("wss://ws-live-data.polymarket.com");

    private final URI clobHost;
    private final URI gammaHost;
    private final URI dataHost;
    private final URI geoblockHost;
    private final URI comboMarketsHost;
    private final URI streamHost;
    private final URI rtdsHost;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final ReadRetryPolicy readRetryPolicy;

    private PolymarketConfig(URI clobHost, URI gammaHost, URI dataHost, URI geoblockHost,
            URI comboMarketsHost, URI streamHost, URI rtdsHost, Duration connectTimeout,
            Duration requestTimeout, ReadRetryPolicy readRetryPolicy) {
        this.clobHost = clobHost;
        this.gammaHost = gammaHost;
        this.dataHost = dataHost;
        this.geoblockHost = geoblockHost;
        this.comboMarketsHost = comboMarketsHost;
        this.streamHost = streamHost;
        this.rtdsHost = rtdsHost;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.readRetryPolicy = readRetryPolicy;
    }

    public static PolymarketConfig defaults() {
        return new PolymarketConfig(DEFAULT_CLOB, DEFAULT_GAMMA, DEFAULT_DATA, DEFAULT_GEOBLOCK,
                DEFAULT_COMBO_MARKETS, DEFAULT_STREAM, DEFAULT_RTDS, Duration.ofSeconds(10),
                Duration.ofSeconds(30), ReadRetryPolicy.defaults());
    }

    public URI clobHost() {
        return clobHost;
    }

    public URI gammaHost() {
        return gammaHost;
    }

    public URI dataHost() {
        return dataHost;
    }

    public URI geoblockHost() {
        return geoblockHost;
    }

    /** The Combo markets catalog; separate from the per-builder RFQ gateway host. */
    public URI comboMarketsHost() {
        return comboMarketsHost;
    }

    /** The CLOB WebSocket base; both stream channels hang off it. */
    public URI streamHost() {
        return streamHost;
    }

    /** The Real-Time Data Service host: a separate protocol and host from the CLOB channels. */
    public URI rtdsHost() {
        return rtdsHost;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public ReadRetryPolicy readRetryPolicy() {
        return readRetryPolicy;
    }

    public PolymarketConfig clobHost(@NonNull URI host) {
        return new PolymarketConfig(host, gammaHost, dataHost, geoblockHost, comboMarketsHost,
                streamHost, rtdsHost, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig gammaHost(@NonNull URI host) {
        return new PolymarketConfig(clobHost, host, dataHost, geoblockHost, comboMarketsHost,
                streamHost, rtdsHost, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig dataHost(@NonNull URI host) {
        return new PolymarketConfig(clobHost, gammaHost, host, geoblockHost, comboMarketsHost,
                streamHost, rtdsHost, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig geoblockHost(@NonNull URI host) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, host, comboMarketsHost,
                streamHost, rtdsHost, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig comboMarketsHost(@NonNull URI host) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost, host,
                streamHost, rtdsHost, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig connectTimeout(@NonNull Duration timeout) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost, comboMarketsHost,
                streamHost, rtdsHost, timeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig requestTimeout(@NonNull Duration timeout) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost, comboMarketsHost,
                streamHost, rtdsHost, connectTimeout, timeout, readRetryPolicy);
    }

    public PolymarketConfig streamHost(@NonNull URI host) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost, comboMarketsHost,
                host, rtdsHost, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig rtdsHost(@NonNull URI host) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost, comboMarketsHost,
                streamHost, host, connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig readRetryPolicy(@NonNull ReadRetryPolicy policy) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost, comboMarketsHost,
                streamHost, rtdsHost, connectTimeout, requestTimeout, policy);
    }
}
