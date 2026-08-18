package com.polymarket.streaming;

/**
 * Receives fully-decoded RTDS events and lifecycle signals from an {@link RtdsConnection}.
 * Implemented by {@link Rtds} itself; a transport never sees a raw frame's consumer.
 */
public interface RtdsEventSink {

    void onBinancePrice(BinancePriceEvent event);

    void onChainlinkPrice(ChainlinkPriceEvent event);

    void onCommentCreated(CommentCreatedEvent event);

    void onCommentRemoved(CommentRemovedEvent event);

    void onReactionCreated(ReactionCreatedEvent event);

    void onReactionRemoved(ReactionRemovedEvent event);

    /** Fires once the transport handshake completes, carrying the new connection generation. */
    void onOpen(long generation);

    /** Fires right after the subscription frame, before any event it elicits — the invalidation point. */
    void onResubscribe(long generation);

    void onError(long generation, Exception error);

    void onClose(long generation, int code, String reason);
}
