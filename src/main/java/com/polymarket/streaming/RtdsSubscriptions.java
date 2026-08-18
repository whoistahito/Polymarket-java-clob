package com.polymarket.streaming;

import java.util.List;

/** The full authoritative RTDS state, polled on every connect and reconnect. */
public record RtdsSubscriptions(
        List<String> binanceSymbols, List<String> chainlinkSymbols, List<CommentSubscription> comments) {

    public RtdsSubscriptions {
        binanceSymbols = List.copyOf(binanceSymbols);
        chainlinkSymbols = List.copyOf(chainlinkSymbols);
        comments = List.copyOf(comments);
    }
}
