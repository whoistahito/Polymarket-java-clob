package com.polymarket.model;

/**
 * Execution instructions for orders.
 */
public enum OrderType {
    /**
     * Good-Till-Cancel: Resting order until filled or canceled.
     */
    GTC,

    /**
     * Fill-Or-Kill: Execute entire order immediately or cancel.
     */
    FOK,

    /**
     * Good-Till-Date: Resting order until specific date.
     */
    GTD,

    /**
     * Fill-And-Kill: Fill what's available immediately, cancel rest.
     */
    FAK
}
