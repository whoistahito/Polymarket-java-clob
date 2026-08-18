package com.polymarket.streaming;

/**
 * Optional lifecycle observer. All methods default to no-op; override only what a caller needs to
 * invalidate cached state or monitor health.
 */
public interface StreamLifecycleListener {

    default void onOpen(StreamChannel channel, long generation) {}

    default void onResubscribe(StreamChannel channel, long generation) {}

    default void onError(StreamChannel channel, long generation, Exception error) {}

    default void onClose(StreamChannel channel, long generation, int code, String reason) {}
}
