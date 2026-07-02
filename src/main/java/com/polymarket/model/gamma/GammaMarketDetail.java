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
        List<GammaClobReward> clobRewards
) {}
