package com.polymarket.streaming;

/** Author profile embedded in a {@link CommentCreatedEvent}. */
public record CommentProfile(
        String baseAddress, boolean displayUsernamePublic, String name, String proxyWallet, String pseudonym) {
}
