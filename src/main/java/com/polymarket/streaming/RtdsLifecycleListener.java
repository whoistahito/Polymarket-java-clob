package com.polymarket.streaming;

/**
 * Optional lifecycle observer. All methods default to no-op; override only what a caller needs to
 * invalidate cached state or monitor health.
 */
public interface RtdsLifecycleListener {

    default void onOpen(long generation) {}

    default void onResubscribe(long generation) {}

    default void onError(long generation, Exception error) {}

    default void onClose(long generation, int code, String reason) {}
}
