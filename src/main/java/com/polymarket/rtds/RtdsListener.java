package com.polymarket.rtds;

/**
 * Callback interface for {@link RtdsClient} messages and lifecycle events.
 *
 * <p>Implementations must be thread-safe — callbacks fire on the OkHttp dispatcher thread.
 */
public interface RtdsListener {

    /** Called for every parsed RTDS message. Use {@code RtdsMessage.as*()} to decode the payload. */
    void onMessage(RtdsMessage message);

    /** Called on a connection or parse error. Default: no-op. */
    default void onError(Exception error) {}

    /** Called when the connection is cleanly closed. Default: no-op. */
    default void onClose(int code, String reason) {}

    /** Called when the connection is (re-)established. Default: no-op. */
    default void onOpen() {}
}
