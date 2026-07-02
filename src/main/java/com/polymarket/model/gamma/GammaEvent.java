package com.polymarket.model.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaEvent(
        String id,
        String ticker,
        String slug,
        String title,
        String subtitle,
        String description,
        String resolutionSource,
        String startDate,
        String endDate,
        String image,
        String icon,
        Boolean active,
        Boolean closed,
        Boolean archived,
        Boolean featured,
        Boolean restricted,
        BigDecimal liquidity,
        BigDecimal volume,
        BigDecimal openInterest,
        String sortBy,
        String category,
        String subcategory,
        String publishedAt,
        String createdAt,
        String updatedAt,
        BigDecimal competitive,
        BigDecimal volume24hr,
        BigDecimal volume1wk,
        BigDecimal volume1mo,
        Boolean negRisk,
        Integer commentCount,
        List<GammaMarketDetail> markets,
        List<GammaSeries> series,
        List<GammaCategory> categories,
        List<GammaTag> tags
) {}
