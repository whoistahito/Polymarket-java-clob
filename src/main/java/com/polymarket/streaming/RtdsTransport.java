package com.polymarket.streaming;

/**
 * Domain-declared port for the RTDS WebSocket transport. The Authoritative Subscription travels
 * with the connect call, so the initial frame is complete however late the socket opens.
 *
 * <p>It is {@link AutoCloseable} because it owns the scheduler, dispatcher and connection pool
 * behind the socket: closing the capability has to release those too, not only the socket.
 */
public interface RtdsTransport extends AutoCloseable {

    RtdsConnection connect(RtdsSubscriptions subscriptions, RtdsEventSink sink);

    /** Idempotent, and never throws: releasing a transport is not an operation that can fail. */
    @Override
    void close();
}
