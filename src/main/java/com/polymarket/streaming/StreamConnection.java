package com.polymarket.streaming;

import java.util.List;

/**
 * A live logical channel connection. Survives reconnects transparently — {@link #subscribe} and
 * {@link #unsubscribe} always act on whatever socket is current, or none while reconnecting.
 */
public interface StreamConnection extends AutoCloseable {

    /** Send a dynamic subscribe update for the given delta (never the initial dump). */
    void subscribe(List<String> ids);

    /** Send a dynamic unsubscribe update for the given delta. */
    void unsubscribe(List<String> ids);

    /** Idempotent: stops the socket, any pending reconnect, and the heartbeat. */
    @Override
    void close();
}
