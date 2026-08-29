package com.polymarket.streaming;

import lombok.NonNull;

/** Author profile embedded in a {@link CommentCreatedEvent}. */
public record CommentProfile(@NonNull String baseAddress, boolean displayUsernamePublic,
        @NonNull String name, @NonNull String proxyWallet, @NonNull String pseudonym) {
}
