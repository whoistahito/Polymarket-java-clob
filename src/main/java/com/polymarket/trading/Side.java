package com.polymarket.trading;

/** Order side, with the official wire encoding. */
public enum Side {
    BUY(0),
    SELL(1);

    private final int wireValue;

    Side(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }
}
