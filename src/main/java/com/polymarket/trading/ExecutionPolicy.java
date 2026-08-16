package com.polymarket.trading;

/** What an immediate order does with liquidity it cannot fill. */
public enum ExecutionPolicy {
    /** Fill completely or not at all. */
    FOK,
    /** Fill what is available and cancel the rest. */
    FAK
}
