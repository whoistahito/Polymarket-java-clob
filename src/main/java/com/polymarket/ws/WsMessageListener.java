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
     * <p>After this callback the connection is considered closed; call
     * {@link WsClient#reconnect()} or create a new {@link WsClient} to resume.
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
}
