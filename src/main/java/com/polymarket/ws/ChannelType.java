package com.polymarket.ws;

/**
 * Identifies which WebSocket channel a connection or event belongs to.
 *
 * <p>Mirrors Rust SDK {@code ChannelType { Market, User }}.
 */
public enum ChannelType {
    /** Unauthenticated market data channel ({@code /ws/market}). */
    MARKET,
    /** Authenticated user-events channel ({@code /ws/user}). */
    USER
}
