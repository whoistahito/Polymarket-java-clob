package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaSeries(
        String id,
        String ticker,
        String slug,
        String title,
        String subtitle,
        String seriesType,
        String recurrence,
        String description,
        String image,
        String icon,
        Boolean active,
        Boolean closed,
        Boolean archived,
        Boolean featured,
        Boolean restricted,
        String publishedAt,
        String createdAt,
        String updatedAt,
        BigDecimal volume24hr,
        BigDecimal volume,
        BigDecimal liquidity,
        String startDate,
        List<GammaEvent> events,
        List<GammaTag> tags
) {}
