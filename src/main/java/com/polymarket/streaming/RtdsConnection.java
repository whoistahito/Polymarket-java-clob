package com.polymarket.streaming;

/**
 * The single live RTDS socket, reconnecting transparently. The connection owns the ordering rule:
 * nothing reaches the wire before this generation's initial frame.
 */
public interface RtdsConnection extends AutoCloseable {

    /**
     * Publishes the current Authoritative Subscription. Before the initial frame goes out the new
     * subjects are folded into it; afterwards only the delta travels as a dynamic update.
     */
    void subscription(RtdsSubscriptions current);

    /** Idempotent: stops the socket, any pending reconnect, and the heartbeat. */
    @Override
    void close();
}
