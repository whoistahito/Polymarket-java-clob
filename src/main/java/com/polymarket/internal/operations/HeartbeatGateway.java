package com.polymarket.internal.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the scheduled {@code POST /v1/heartbeats} tick — the CLOB dead-man switch that cancels
 * open orders once beats stop arriving. Ground truth: the documented contract sends an empty
 * {@code heartbeat_id} on the first tick, then chains each response's id into the next tick.
 * A failed tick is logged and left scheduled; only {@link #stop()}/{@link #close()} cancels it.
 */
public final class HeartbeatGateway implements AutoCloseable {

    /** The documented interval: a beat every 5 s (orders are cancelled after a 10 s silence). */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(HeartbeatGateway.class);
    private static final String HEARTBEAT_PATH = "/v1/heartbeats";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();
    private final AtomicReference<String> lastId = new AtomicReference<>();

    public HeartbeatGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the repeating tick. The first tick's {@code heartbeat_id} is empty per the
     * documented contract; every later tick chains the id the previous response returned.
     */
    public void start(Duration interval, ApiCredentials credentials, String address) {
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive, got: " + interval);
        }
        if (!active.compareAndSet(false, true)) {
            throw new IllegalStateException("heartbeat is already active; call stop() first");
        }
        lastId.set(null);
        long ms = interval.toMillis();
        try {
            task.set(scheduler.scheduleAtFixedRate(
                    () -> tick(credentials, address), ms, ms, TimeUnit.MILLISECONDS));
        } catch (RejectedExecutionException e) {
            active.set(false); // the scheduler was already shut down by close()
            throw e;
        }
    }

    /** Cancels the scheduled tick. Does nothing if not currently active. */
    public void stop() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> scheduled = task.getAndSet(null);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        lastId.set(null);
    }

    public boolean isActive() {
        return active.get();
    }

    /** Stops the tick and shuts down the owned scheduler. Safe to call more than once. */
    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }

    private void tick(ApiCredentials credentials, String address) {
        try {
            String previous = lastId.get();
            String next = post(credentials, address, previous == null ? "" : previous);
            if (next != null) {
                lastId.set(next);
            }
        } catch (Exception e) {
            // Swallowed deliberately: the tick stays scheduled and retries next interval.
            log.warn("heartbeat tick failed, will retry: {}", e.toString());
        }
    }

    private String post(ApiCredentials credentials, String address, String heartbeatId) throws IOException {
        String body;
        try {
            body = json.writeValueAsString(Map.of("heartbeat_id", heartbeatId));
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize the heartbeat body", e);
        }
        Map<String, String> headers = L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), "POST", HEARTBEAT_PATH, body);
        HttpOutcome outcome = runtime.post(config.clobHost(), HEARTBEAT_PATH, headers, body);
        if (!outcome.successful()) {
            throw new IOException("heartbeat failed with HTTP " + outcome.status());
        }
        JsonNode node = runtime.parse(outcome.body());
        String next = node.path("heartbeat_id").asText(null);
        return next == null || next.isBlank() ? null : next;
    }
}
