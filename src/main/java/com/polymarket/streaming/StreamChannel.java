package com.polymarket.streaming;

/** Identifies which CLOB WebSocket channel a connection, generation, or event belongs to. */
public enum StreamChannel {
    /** Unauthenticated market data channel ({@code /ws/market}). */
    MARKET,
    /** L2-authenticated user-events channel ({@code /ws/user}). */
    USER
}
