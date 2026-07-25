package com.polymarket.ws;

import com.polymarket.ws.model.WsMessage;

/**
 * Callback interface for receiving WebSocket messages from Polymarket.
 *
 * <p>Implementations must be thread-safe because callbacks may be invoked
 * from the OkHttp dispatcher thread.
 */
public interface WsMessageListener {

    /**
     * Called for every successfully parsed WebSocket message.
     *
     * @param message the decoded message (cast to a specific subtype if needed)
     */
    void onMessage(WsMessage message);

    /**
     * Called when a WebSocket error occurs (network failure, parse error, etc.).
     *
     * <p>After this callback {@link WsClient} automatically schedules a reconnect unless the client
     * was explicitly closed or its retry limit was reached.
     *
     * @param error the cause of the failure
     */
    void onError(Exception error);

    /**
     * Called when the WebSocket connection is cleanly closed by the server or
     * by an explicit call to {@link WsClient#close()}.
     *
     * @param code   WebSocket close code
     * @param reason human-readable close reason
     */
    void onClose(int code, String reason);

    /**
     * Called when the WebSocket connection is successfully (re-)established.
     *
     * <p>Default implementation is a no-op; override to react to connect events.
     */
    default void onOpen() {}

    // --------------------------------------------------------------------- //
    // Channel-identified lifecycle (Ticket 027)                              //
    // --------------------------------------------------------------------- //

    /**
     * Called when a specific channel is (re-)established, with its connection generation.
     *
     * <p>The generation starts at 1 for a channel's first connection and increments on every
     * reconnect, so a consumer can tell whether cached state predates the current connection: any
     * book snapshot taken under an older generation is stale and must not be mixed with fresh data.
     *
     * <p>Defaults to the channel-agnostic {@link #onOpen()} so existing implementations keep working.
     *
     * @param channel    {@link ChannelType#MARKET} or {@link ChannelType#USER}
     * @param generation 1-based connection counter for that channel
     */
    default void onOpen(ChannelType channel, long generation) { onOpen(); }

    /**
     * Called when a specific channel fails, with the generation that failed.
     *
     * <p>A reconnect is scheduled regardless of whether this method throws.
     *
     * @param channel    the channel that failed
     * @param generation the generation that was live when the failure happened
     * @param error      the cause
     */
    default void onError(ChannelType channel, long generation, Exception error) { onError(error); }

    /**
     * Called when a specific channel closes, with the generation that closed.
     *
     * <p>A reconnect is scheduled regardless of whether this method throws.
     *
     * @param channel    the channel that closed
     * @param generation the generation that was live when it closed
     * @param code       WebSocket close code
     * @param reason     human-readable close reason
     */
    default void onClose(ChannelType channel, long generation, int code, String reason) {
        onClose(code, reason);
    }

    /**
     * Called immediately after a subscription frame is sent on a channel and BEFORE any frame it
     * elicits is dispatched.
     *
     * <p>This is the point at which a consumer should clear freshness flags: everything received
     * after it belongs to {@code generation}, everything before it does not. Fires on the first
     * subscription and on every resubscription after a reconnect.
     *
     * @param channel    the channel that was (re-)subscribed
     * @param generation the generation the subscription belongs to
     */
    default void onResubscribe(ChannelType channel, long generation) {}
}
