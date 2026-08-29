package com.polymarket.streaming;

import java.util.List;
import lombok.NonNull;

/** The full authoritative RTDS state, polled on every connect and reconnect. */
public record RtdsSubscriptions(@NonNull List<String> binanceSymbols,
        @NonNull List<String> chainlinkSymbols, @NonNull List<CommentSubscription> comments) {

    public RtdsSubscriptions {
        binanceSymbols = List.copyOf(binanceSymbols);
        chainlinkSymbols = List.copyOf(chainlinkSymbols);
        comments = List.copyOf(comments);
    }
}
