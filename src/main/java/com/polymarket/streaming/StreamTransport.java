package com.polymarket.streaming;

import com.polymarket.authentication.ApiCredentials;
import java.util.List;
import java.util.function.Supplier;

/**
 * Domain-declared port for the CLOB WebSocket transport. {@code idSupplier} is polled on every
 * open and reopen, so a reconnect needs no state outside {@link Streaming}'s authoritative set.
 */
public interface StreamTransport {

    StreamConnection connectMarket(Supplier<List<String>> idSupplier, StreamEventSink sink);

    StreamConnection connectUser(
            ApiCredentials credentials, Supplier<List<String>> marketSupplier, StreamEventSink sink);
}
