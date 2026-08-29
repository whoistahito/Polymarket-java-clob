package com.polymarket.trading;

/** What an immediate order does with liquidity it cannot fill. */
public enum ExecutionPolicy {
    /** Fill completely or not at all. */
    FOK,
    /** Fill what is available and cancel the rest. */
    FAK;

    /** The wire order type this policy asks for. */
    public OrderType orderType() {
        return this == FOK ? OrderType.FOK : OrderType.FAK;
    }
}
