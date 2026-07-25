package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.polymarket.util.JsonEmbeddedListDeserializer;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaMarketDetail(
        String id,
        String question,
        String conditionId,
        String slug,
        String category,
        String endDate,
        String startDate,
        BigDecimal liquidity,
        BigDecimal volume,
        Boolean active,
        Boolean closed,
        Boolean archived,
        Boolean featured,
        Boolean restricted,
        String description,

        @JsonDeserialize(using = JsonEmbeddedListDeserializer.class)
        List<String> outcomes,

        @JsonDeserialize(using = JsonEmbeddedListDeserializer.class)
        List<String> outcomePrices,

        @JsonDeserialize(using = JsonEmbeddedListDeserializer.class)
        List<String> clobTokenIds,

        Boolean enableOrderBook,
        Boolean acceptingOrders,
        BigDecimal volume24hr,
        BigDecimal volume1wk,
        BigDecimal lastTradePrice,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        Boolean negRisk,
        BigDecimal spread,
        List<GammaTag> tags,
        List<GammaEvent> events,
        List<GammaClobReward> clobRewards,

        /**
         * Minimum price increment for this market, exact (Ticket 024). Null when the response
         * omitted it, so a caller can fail closed rather than assume a tick.
         */
        BigDecimal orderPriceMinTickSize,

        /** Minimum order size in shares, exact (Ticket 024). Null when the response omitted it. */
        BigDecimal orderMinSize
) {

    /**
     * This market's order-construction rules as one typed value (Ticket 024), ready to hand to
     * order construction without a {@code double} round trip.
     */
    public com.polymarket.model.MarketRules marketRules() {
        return com.polymarket.model.MarketRules.of(orderPriceMinTickSize, orderMinSize);
    }
}
