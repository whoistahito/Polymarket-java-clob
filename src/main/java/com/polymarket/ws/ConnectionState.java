package com.polymarket.ws;

import java.time.Instant;

/**
 * Sealed type representing the lifecycle state of a WebSocket channel connection.
 *
 * <p>Mirrors Rust SDK {@code ConnectionState { Disconnected, Connecting, Connected{since}, Reconnecting{attempt} }}.
 *
 * <p>Usage:
 * <pre>{@code
 * ConnectionState state = client.getConnectionState(ChannelType.MARKET);
 * if (state.isConnected()) { ... }
 * if (state instanceof ConnectionState.Reconnecting r) {
 *     System.out.println("attempt " + r.attempt());
 * }
 * }</pre>
 */
public sealed interface ConnectionState
    permits ConnectionState.Disconnected,
            ConnectionState.Connecting,
            ConnectionState.Connected,
            ConnectionState.Reconnecting {

    /** Returns {@code true} if the channel is in the {@link Connected} state. */
    default boolean isConnected() {
        return this instanceof Connected;
    }

    // ------------------------------------------------------------------ //
    // States                                                               //
    // ------------------------------------------------------------------ //

    /** Channel is not connected and no reconnect is scheduled. */
    record Disconnected() implements ConnectionState {}

    /** Channel is in the process of opening a connection. */
    record Connecting() implements ConnectionState {}

    /**
     * Channel is fully connected and operational.
     *
     * @param since the instant the connection was established
     */
    record Connected(Instant since) implements ConnectionState {}

    /**
     * Channel lost its connection and is waiting to retry.
     *
     * @param attempt the 1-based reconnection attempt number
     */
    record Reconnecting(int attempt) implements ConnectionState {}

    // ------------------------------------------------------------------ //
    // Static factories for convenience                                     //
    // ------------------------------------------------------------------ //

    static ConnectionState disconnected()             { return new Disconnected(); }
    static ConnectionState connecting()               { return new Connecting(); }
    static ConnectionState connected()                { return new Connected(Instant.now()); }
    static ConnectionState connected(Instant since)   { return new Connected(since); }
    static ConnectionState reconnecting(int attempt)  { return new Reconnecting(attempt); }
}
