package com.polymarket;

import java.net.URI;
import java.time.Duration;

/**
 * SDK-owned network configuration. Exposes only JDK types, so a transport-library
 * upgrade cannot break a caller's source.
 */
public final class PolymarketConfig {

    private static final URI DEFAULT_CLOB = URI.create("https://clob.polymarket.com");
    private static final URI DEFAULT_GAMMA = URI.create("https://gamma-api.polymarket.com");
    private static final URI DEFAULT_DATA = URI.create("https://data-api.polymarket.com");
    private static final URI DEFAULT_GEOBLOCK = URI.create("https://polymarket.com");

    private final URI clobHost;
    private final URI gammaHost;
    private final URI dataHost;
    private final URI geoblockHost;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final ReadRetryPolicy readRetryPolicy;

    private PolymarketConfig(URI clobHost, URI gammaHost, URI dataHost, URI geoblockHost,
            Duration connectTimeout, Duration requestTimeout, ReadRetryPolicy readRetryPolicy) {
        this.clobHost = clobHost;
        this.gammaHost = gammaHost;
        this.dataHost = dataHost;
        this.geoblockHost = geoblockHost;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.readRetryPolicy = readRetryPolicy;
    }

    public static PolymarketConfig defaults() {
        return new PolymarketConfig(DEFAULT_CLOB, DEFAULT_GAMMA, DEFAULT_DATA, DEFAULT_GEOBLOCK,
                Duration.ofSeconds(10), Duration.ofSeconds(30), ReadRetryPolicy.defaults());
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

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public ReadRetryPolicy readRetryPolicy() {
        return readRetryPolicy;
    }

    public PolymarketConfig clobHost(URI host) {
        return new PolymarketConfig(require(host), gammaHost, dataHost, geoblockHost,
                connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig gammaHost(URI host) {
        return new PolymarketConfig(clobHost, require(host), dataHost, geoblockHost,
                connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig dataHost(URI host) {
        return new PolymarketConfig(clobHost, gammaHost, require(host), geoblockHost,
                connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig geoblockHost(URI host) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, require(host),
                connectTimeout, requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig connectTimeout(Duration timeout) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost,
                require(timeout), requestTimeout, readRetryPolicy);
    }

    public PolymarketConfig requestTimeout(Duration timeout) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost,
                connectTimeout, require(timeout), readRetryPolicy);
    }

    public PolymarketConfig readRetryPolicy(ReadRetryPolicy policy) {
        return new PolymarketConfig(clobHost, gammaHost, dataHost, geoblockHost,
                connectTimeout, requestTimeout, require(policy));
    }

    private static <T> T require(T value) {
        return value;
    }
}
