package com.polymarket.streaming;

import java.util.function.Supplier;

/**
 * Domain-declared port for the RTDS WebSocket transport. {@code stateSupplier} is polled on every
 * open and reopen, so a reconnect needs no state outside {@link Rtds}'s authoritative sets.
 */
public interface RtdsTransport {

    RtdsConnection connect(Supplier<RtdsSubscriptions> stateSupplier, RtdsEventSink sink);
}
