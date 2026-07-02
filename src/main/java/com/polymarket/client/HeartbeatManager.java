package com.polymarket.client;

import com.polymarket.model.HeartbeatResponse;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages automatic heartbeat posting to keep open orders active.
 *
 * <p>Spawns a background scheduled task that calls {@link PolymarketClient#postHeartbeat}
 * at a fixed interval, chaining the returned {@code heartbeat_id} into each subsequent
 * request (mirrors Rust SDK's {@code heartbeats} feature).
 *
 * <p>Errors are logged at {@code WARN} level but do not stop the background task — the
 * task remains scheduled and will retry on the next tick.
 *
 * <p>Usage:
 * <pre>{@code
 * HeartbeatManager hb = new HeartbeatManager(client);
 * hb.start(5_000); // post every 5 s
 * // … trading …
 * hb.stop();
 * }</pre>
 */
public final class HeartbeatManager {

    /** Default heartbeat interval matching the Rust SDK (5 seconds). */
    public static final long DEFAULT_INTERVAL_MS = 5_000L;

    private static final Logger log = LoggerFactory.getLogger(HeartbeatManager.class);

    /** Functional interface so we can inject the posting logic for testing. */
    @FunctionalInterface
    public interface HeartbeatPoster {
        HeartbeatResponse post(String heartbeatId) throws IOException;
    }

    private final HeartbeatPoster poster;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> taskRef = new AtomicReference<>();
    /** Heartbeat ID returned by the last successful call; chained into the next request. */
    private final AtomicReference<String> lastHeartbeatId = new AtomicReference<>(null);

    /**
     * Creates a manager backed by a single-thread daemon scheduler.
     *
     * @param poster callback that performs the actual HTTP POST
     */
    public HeartbeatManager(HeartbeatPoster poster) {
        this.poster = poster;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts automatic heartbeat posting with the {@link #DEFAULT_INTERVAL_MS default interval}.
     *
     * @throws IllegalStateException if heartbeats are already active
     */
    public void start() {
        start(DEFAULT_INTERVAL_MS);
    }

    /**
     * Starts automatic heartbeat posting with a custom interval.
     *
     * @param intervalMs how often to post heartbeats in milliseconds (must be &gt; 0)
     * @throws IllegalArgumentException if {@code intervalMs} is not positive
     * @throws IllegalStateException    if heartbeats are already active
     */
    public void start(long intervalMs) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive, got: " + intervalMs);
        }
        if (!active.compareAndSet(false, true)) {
            throw new IllegalStateException("Heartbeats are already active; call stop() first");
        }
        lastHeartbeatId.set(null);

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
        taskRef.set(task);
        log.info("Heartbeat manager started (interval={}ms)", intervalMs);
    }

    /**
     * Stops automatic heartbeat posting and waits for any in-flight task to complete.
     *
     * <p>Does nothing if heartbeats are not currently active.
     */
    public void stop() {
        if (!active.compareAndSet(true, false)) {
            return; // already stopped
        }
        ScheduledFuture<?> task = taskRef.getAndSet(null);
        if (task != null) {
            task.cancel(false); // let the current tick finish if running
        }
        lastHeartbeatId.set(null);
        log.info("Heartbeat manager stopped");
    }

    /**
     * Returns {@code true} if the background heartbeat task is currently running.
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Returns the heartbeat ID from the most recent successful response, or {@code null}
     * if no heartbeat has succeeded yet.
     */
    public String getLastHeartbeatId() {
        return lastHeartbeatId.get();
    }

    /**
     * Shuts down the underlying scheduler permanently. After calling this method the
     * manager cannot be restarted.
     */
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    // ----------------------------------------------------------------------- //
    // Internal                                                                  //
    // ----------------------------------------------------------------------- //

    private void sendHeartbeat() {
        try {
            HeartbeatResponse response = poster.post(lastHeartbeatId.get());
            if (response != null && response.getHeartbeatId() != null) {
                lastHeartbeatId.set(response.getHeartbeatId());
                log.debug("Heartbeat sent, id={}", response.getHeartbeatId());
            }
        } catch (Exception e) {
            // Log but do not rethrow — keeps the task scheduled for the next tick
            log.warn("Heartbeat failed (will retry): {}", e.getMessage());
        }
    }
}
