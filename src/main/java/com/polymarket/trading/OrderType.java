package com.polymarket.trading;

/** GTC rests until cancelled, GTD expires, FOK/FAK execute immediately (all-or-nothing vs partial). */
public enum OrderType {
    GTC, GTD, FOK, FAK
}
