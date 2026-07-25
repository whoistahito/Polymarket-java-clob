package com.polymarket.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * The two order-construction rules a market imposes: its price tick and its minimum order size
 * (Ticket 024).
 *
 * <p>Both are exact decimals on the wire and both are money rules, so they are modelled as
 * {@link BigDecimal} and never pass through {@code double}. A market's published minimum can carry
 * more precision than a {@code double} can hold (the exchange serves values like
 * {@code 5.0000000000000001}), and the tick decides which price grid an order may be signed against.
 *
 * <p>Both fields are nullable on purpose: a caller that cannot read a rule must be able to see that
 * and fail closed, rather than receive a plausible default it will happily trade on.
 *
 * <p>Field names follow the Gamma spelling ({@code orderPriceMinTickSize} / {@code orderMinSize});
 * the CLOB spellings ({@code minimum_tick_size} / {@code minimum_order_size}) and the abbreviated
 * {@code clob-markets} spellings ({@code mts} / {@code mos}) are accepted as aliases, so one type
 * covers every documented market response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketRules(

    /** Minimum price increment, e.g. {@code 0.01}, {@code 0.005}, {@code 0.0025}. */
    @JsonProperty("orderPriceMinTickSize")
    @JsonAlias({"minimum_tick_size", "minimumTickSize", "tick_size", "tickSize", "mts"})
    BigDecimal orderPriceMinTickSize,

    /** Minimum order size in shares, e.g. {@code 5}. */
    @JsonProperty("orderMinSize")
    @JsonAlias({"minimum_order_size", "minimumOrderSize", "min_order_size", "minOrderSize", "mos"})
    BigDecimal orderMinSize) {

    /** Rules with both fields absent — the fail-closed starting point. */
    public static final MarketRules EMPTY = new MarketRules(null, null);

    /** Build rules directly, e.g. from a market model that already carries both fields. */
    public static MarketRules of(BigDecimal tickSize, BigDecimal minSize) {
        return new MarketRules(tickSize, minSize);
    }

    /** True when both rules were present in the response. Says nothing about whether they are sane. */
    @JsonIgnore
    public boolean isComplete() {
        return orderPriceMinTickSize != null && orderMinSize != null;
    }

    /**
     * True when both rules are present AND in range: a tick strictly inside {@code (0, 1)} and a
     * strictly positive minimum. A caller that gates order placement should test this, not
     * {@link #isComplete()} — a zero tick would divide by zero during rounding and a zero minimum
     * would wave through an order the exchange refuses.
     */
    @JsonIgnore
    public boolean isValid() {
        return isComplete()
            && orderPriceMinTickSize.signum() > 0
            && orderPriceMinTickSize.compareTo(BigDecimal.ONE) < 0
            && orderMinSize.signum() > 0;
    }

    /**
     * The tick as the plain string {@link CreateOrderOptions} expects, or {@code null} when absent.
     *
     * <p>Uses {@code toPlainString} so a tick never reaches order construction in scientific
     * notation, which no documented tick spelling uses.
     */
    @JsonIgnore
    public String tickSizeString() {
        return orderPriceMinTickSize == null ? null : orderPriceMinTickSize.toPlainString();
    }

    /**
     * These rules as {@link CreateOrderOptions}, ready to hand to order construction with no
     * {@code double} round trip in between.
     *
     * @param negRisk whether the market is a neg-risk market
     */
    @JsonIgnore
    public CreateOrderOptions toCreateOrderOptions(boolean negRisk) {
        return CreateOrderOptions.builder()
            .tickSize(tickSizeString())
            .negRisk(negRisk)
            .orderMinSize(orderMinSize)
            .build();
    }
}
