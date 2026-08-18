package com.polymarket.streaming;

/**
 * Receives fully-decoded events and lifecycle signals from a {@link StreamConnection}. Implemented
 * by {@link Streaming} itself; a {@link StreamTransport} never sees a raw frame's consumer.
 */
public interface StreamEventSink {

    void onBook(BookEvent event);

    void onPriceChange(PriceChangeEvent event);

    void onLastTradePrice(LastTradePriceEvent event);

    void onTickSizeChange(TickSizeChangeEvent event);

    void onOrder(OrderEvent event);

    void onTrade(TradeEvent event);

    /** Fires once the transport handshake completes, carrying the new connection generation. */
    void onOpen(StreamChannel channel, long generation);

    /** Fires right after the subscription frame, before any event it elicits — the invalidation point. */
    void onResubscribe(StreamChannel channel, long generation);

    void onError(StreamChannel channel, long generation, Exception error);

    void onClose(StreamChannel channel, long generation, int code, String reason);
}
