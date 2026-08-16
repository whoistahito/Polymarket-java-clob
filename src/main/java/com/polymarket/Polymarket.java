package com.polymarket;

import com.polymarket.authentication.Authentication;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.internal.authentication.AuthenticationGateway;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.markets.MarketsGateway;
import com.polymarket.internal.operations.OperationsGateway;
import com.polymarket.internal.rewards.RewardsGateway;
import com.polymarket.markets.Markets;
import com.polymarket.operations.GeoblockStatus;
import com.polymarket.operations.ServerTime;
import com.polymarket.operations.ServiceHealth;
import com.polymarket.rewards.Rewards;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point to the Polymarket SDK. Construction needs no credentials and performs no
 * network call; it is thread-safe and closing it releases owned resources once.
 */
public final class Polymarket implements AutoCloseable {

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final OperationsGateway operations;
    private final Authentication authentication;
    private final Markets markets;
    private final Rewards rewards;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Polymarket(PolymarketConfig config, HttpRuntime runtime, SigningAuthority authority,
            Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.operations = new OperationsGateway(config, runtime);
        this.authentication = new Authentication(authority,
                new AuthenticationGateway(config, runtime, clock));
        this.markets = new Markets(new MarketsGateway(config, runtime));
        this.rewards = new Rewards(authority, new RewardsGateway(config, runtime, clock));
    }

    public static Polymarket withDefaults() {
        return with(PolymarketConfig.defaults());
    }

    public static Polymarket with(PolymarketConfig config) {
        return with(config, SigningAuthority.none());
    }

    public static Polymarket with(PolymarketConfig config, SigningAuthority authority) {
        Objects.requireNonNull(config, "config");
        return new Polymarket(config, new HttpRuntime(config.connectTimeout(),
                config.requestTimeout(), config.readRetryPolicy()), authority, Clock.systemUTC());
    }

    /** Test seam: lets a caller supply a runtime whose backoff does not sleep. */
    static Polymarket with(PolymarketConfig config, HttpRuntime runtime) {
        return with(config, runtime, SigningAuthority.none(), Clock.systemUTC());
    }

    static Polymarket with(PolymarketConfig config, HttpRuntime runtime,
            SigningAuthority authority, Clock clock) {
        return new Polymarket(Objects.requireNonNull(config), Objects.requireNonNull(runtime),
                Objects.requireNonNull(authority), Objects.requireNonNull(clock));
    }

    public PolymarketConfig config() {
        return config;
    }

    /** API-key lifecycle. Reachable without credentials; each call checks its own authority. */
    public Authentication authentication() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return authentication;
    }

    /** Canonical market discovery over Gamma. Needs no credentials. */
    public Markets markets() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return markets;
    }

    /** Market reward programmes and user earnings, one typed cursor page at a time. */
    public Rewards rewards() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return rewards;
    }

    public ServerTime serverTime() throws IOException {
        return open().serverTime();
    }

    /** Probes every service; an unreachable service is reported, not thrown. */
    public List<ServiceHealth> health() {
        return open().health();
    }

    public GeoblockStatus geoblock() throws IOException {
        return open().geoblock();
    }

    private OperationsGateway open() {
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        return operations;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            runtime.close();
        }
    }
}
