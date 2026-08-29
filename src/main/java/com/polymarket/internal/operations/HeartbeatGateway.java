package com.polymarket.internal.operations;

import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.internal.authentication.L2Attestation;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the scheduled dead-man-switch tick: a bodyless L2-signed {@code POST /heartbeats} whose
 * whole acknowledgement is the response status (clob-openapi.yaml, operation {@code sendHeartbeat}).
 * A failed tick is logged and left scheduled; only {@link #stop()}/{@link #close()} cancels it.
 */
public final class HeartbeatGateway implements AutoCloseable {

    /** A local default only: no official page publishes a beat interval or a silence timeout. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(HeartbeatGateway.class);
    private static final String HEARTBEAT_PATH = "/heartbeats";
    /** The operation declares no requestBody, so the L2 payload signs an empty body. */
    private static final String NO_BODY = "";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();

    public HeartbeatGateway(@NonNull PolymarketConfig config, @NonNull HttpRuntime runtime,
            @NonNull Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the repeating tick. Idempotent: an already-active Heartbeat keeps its one schedule. */
    public void start(@NonNull Duration interval, @NonNull ApiCredentials credentials,
            @NonNull String address) {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive, got: " + interval);
        }
        if (!active.compareAndSet(false, true)) {
            return;
        }
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
            beat(credentials, address);
        } catch (Exception e) {
            // Swallowed deliberately: dropping the schedule would have the exchange cancel orders.
            log.warn("heartbeat tick failed, staying scheduled: {}", e.toString());
        }
    }

    private void beat(ApiCredentials credentials, String address) throws IOException {
        Map<String, String> headers = L2Attestation.headers(credentials, address,
                clock.instant().getEpochSecond(), "POST", HEARTBEAT_PATH, NO_BODY);
        HttpOutcome outcome = runtime.post(config.clobHost(), HEARTBEAT_PATH, headers, NO_BODY);
        if (!outcome.successful()) {
            throw new IOException("heartbeat was not acknowledged: HTTP " + outcome.status());
        }
    }
}
