package com.polymarket.streaming;

/**
 * Domain-declared port for the RTDS WebSocket transport. The Authoritative Subscription travels
 * with the connect call, so the initial frame is complete however late the socket opens.
 */
public interface RtdsTransport {

    RtdsConnection connect(RtdsSubscriptions subscriptions, RtdsEventSink sink);
}
