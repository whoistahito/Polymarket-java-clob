package com.polymarket.rfq;

/**
 * The Builder Gateway's six-decimal Quote amounts, kept as the {@code *_e6} base units the wire
 * carries. {@code totalRequired} is collateral for BUY and Combo shares for SELL;
 * {@code netReceive} is the reverse and is authoritative — never derive it from the blended price.
 */
public record QuoteAmounts(
        long blendedPriceBaseUnits,
        long makerAmountBaseUnits,
        long takerAmountBaseUnits,
        long totalRequiredBaseUnits,
        long netReceiveBaseUnits) {

    public QuoteAmounts {
        if (blendedPriceBaseUnits <= 0 || makerAmountBaseUnits <= 0 || takerAmountBaseUnits <= 0
                || totalRequiredBaseUnits <= 0 || netReceiveBaseUnits <= 0) {
            throw new IllegalArgumentException("quote amounts must all be positive");
        }
    }
}
