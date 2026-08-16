package com.polymarket;

import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.operations.OperationsGateway;
import com.polymarket.operations.GeoblockStatus;
import com.polymarket.operations.ServerTime;
import com.polymarket.operations.ServiceHealth;
import java.io.IOException;
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
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Polymarket(PolymarketConfig config, HttpRuntime runtime) {
        this.config = config;
        this.runtime = runtime;
        this.operations = new OperationsGateway(config, runtime);
    }

    public static Polymarket withDefaults() {
        return with(PolymarketConfig.defaults());
    }

    public static Polymarket with(PolymarketConfig config) {
        Objects.requireNonNull(config, "config");
        return new Polymarket(config, new HttpRuntime(
                config.connectTimeout(), config.requestTimeout(), config.readRetryPolicy()));
    }

    /** Test seam: lets a caller supply a runtime whose backoff does not sleep. */
    static Polymarket with(PolymarketConfig config, HttpRuntime runtime) {
        return new Polymarket(Objects.requireNonNull(config), Objects.requireNonNull(runtime));
    }

    public PolymarketConfig config() {
        return config;
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
