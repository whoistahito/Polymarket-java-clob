package com.polymarket.streaming;

import java.util.List;

/**
 * A live logical channel connection, surviving reconnects transparently. The connection owns the
 * ordering rule: nothing reaches the wire before this generation's initial frame.
 */
public interface StreamConnection extends AutoCloseable {

    /**
     * Publishes the current Authoritative Subscription. Before the initial frame goes out the new
     * subjects are folded into it; afterwards only the delta travels as a dynamic update.
     */
    void subscription(List<String> subjects);

    /** Idempotent: stops the socket, any pending reconnect, and the heartbeat. */
    @Override
    void close();
}
