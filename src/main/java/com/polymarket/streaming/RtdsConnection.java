package com.polymarket.streaming;

import java.util.List;

/**
 * The single live RTDS socket, reconnecting transparently across however many physical sockets it
 * takes. Each subscribe/unsubscribe call sends only its delta; the full state is resent on open.
 */
public interface RtdsConnection extends AutoCloseable {

    void subscribeBinance(List<String> symbols);

    void unsubscribeBinance(List<String> symbols);

    void subscribeChainlink(List<String> symbols);

    void unsubscribeChainlink(List<String> symbols);

    void subscribeComments(List<CommentSubscription> subscriptions);

    void unsubscribeComments(List<CommentSubscription> subscriptions);

    /** Idempotent: stops the socket, any pending reconnect, and the heartbeat. */
    @Override
    void close();
}
